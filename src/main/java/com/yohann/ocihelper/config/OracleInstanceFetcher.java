package com.yohann.ocihelper.config;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.core.BlockstorageClient;
import com.oracle.bmc.core.ComputeClient;
import com.oracle.bmc.core.ComputeWaiters;
import com.oracle.bmc.core.VirtualNetworkClient;
import com.oracle.bmc.core.model.*;
import com.oracle.bmc.core.requests.*;
import com.oracle.bmc.core.responses.*;
import com.oracle.bmc.identity.IdentityClient;
import com.oracle.bmc.identity.model.*;
import com.oracle.bmc.identity.requests.*;
import com.oracle.bmc.identity.responses.*;
import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.monitoring.MonitoringClient;
import com.oracle.bmc.workrequests.WorkRequestClient;
import com.yohann.ocihelper.bean.Tuple2;
import com.yohann.ocihelper.bean.constant.CacheConstant;
import com.yohann.ocihelper.bean.dto.InstanceCfgDTO;
import com.yohann.ocihelper.bean.dto.InstanceDetailDTO;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.params.oci.securityrule.UpdateSecurityRuleListParams;
import com.yohann.ocihelper.bean.response.oci.cfg.OciCfgDetailsRsp;
import com.yohann.ocihelper.enums.*;
import com.yohann.ocihelper.exception.OciException;
import com.yohann.ocihelper.utils.CommonUtils;
import com.yohann.ocihelper.utils.CustomExpiryGuavaCache;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.oracle.bmc.core.model.CreatePublicIpDetails.Lifetime.Ephemeral;
import static com.yohann.ocihelper.service.impl.OciServiceImpl.TASK_MAP;

/**
 * <p>
 * OracleInstanceFetcher
 * </p >
 *
 * @author yuhui.fan
 * @since 2024/11/1 15:55
 */
@Slf4j
@Data
public class OracleInstanceFetcher implements Closeable {

    private final ComputeClient computeClient;
    private final IdentityClient identityClient;
    private final WorkRequestClient workRequestClient;
    private final VirtualNetworkClient virtualNetworkClient;
    private final BlockstorageClient blockstorageClient;
    private final MonitoringClient monitoringClient;
    private SysUserDTO user;
    private String compartmentId;

    private static final String CIDR_BLOCK = "10.0.0.0/16";

    @Override
    public void close() {
        computeClient.close();
        identityClient.close();
        workRequestClient.close();
        virtualNetworkClient.close();
        blockstorageClient.close();
        monitoringClient.close();
    }

    public OracleInstanceFetcher(SysUserDTO user) {
        this.user = user;
        SysUserDTO.OciCfg ociCfg = user.getOciCfg();
        SimpleAuthenticationDetailsProvider provider = SimpleAuthenticationDetailsProvider.builder()
                .tenantId(ociCfg.getTenantId())
                .userId(ociCfg.getUserId())
                .fingerprint(ociCfg.getFingerprint())
                .privateKeySupplier(() -> {
                    try (FileInputStream fis = new FileInputStream(ociCfg.getPrivateKeyPath());
                         ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            baos.write(buffer, 0, bytesRead);
                        }
                        return new ByteArrayInputStream(baos.toByteArray());
                    } catch (Exception e) {
                        throw new RuntimeException("获取密钥失败");
                    }
                })
                .region(Region.valueOf(ociCfg.getRegion()))
                .build();

        identityClient = IdentityClient.builder().build(provider);
        computeClient = ComputeClient.builder().build(provider);
        blockstorageClient = BlockstorageClient.builder().build(provider);
        workRequestClient = WorkRequestClient.builder().build(provider);
        virtualNetworkClient = VirtualNetworkClient.builder().build(provider);
        monitoringClient = MonitoringClient.builder().build(provider);
        compartmentId = StrUtil.isBlank(ociCfg.getCompartmentId()) ? findRootCompartment(identityClient, provider.getTenantId()) : ociCfg.getCompartmentId();
    }

    synchronized public InstanceDetailDTO createInstanceData() {
        InstanceDetailDTO instanceDetailDTO = new InstanceDetailDTO();
        instanceDetailDTO.setTaskId(user.getTaskId());
        instanceDetailDTO.setUsername(user.getUsername());
        instanceDetailDTO.setRegion(user.getOciCfg().getRegion());
        instanceDetailDTO.setArchitecture(user.getArchitecture());
        instanceDetailDTO.setCreateNumbers(user.getCreateNumbers());

        List<AvailabilityDomain> availabilityDomains = getAvailabilityDomains(identityClient, compartmentId);
        int size = availabilityDomains.size();

        List<String> shapeList = availabilityDomains.parallelStream().map(availabilityDomain ->
                        computeClient.listShapes(ListShapesRequest.builder()
                                .availabilityDomain(availabilityDomain.getName())
                                .compartmentId(compartmentId)
                                .build()).getItems())
                .flatMap(Collection::stream)
                .map(Shape::getShape)
                .distinct()
                .collect(Collectors.toList());
        ArchitectureEnum type = ArchitectureEnum.getType(user.getArchitecture());
        if (shapeList.isEmpty() || !shapeList.contains(type.getShapeDetail())) {
            instanceDetailDTO.setNoShape(true);
            log.error("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] 开机失败，该区域可能不支持 CPU 架构：{}",
                    user.getUsername(), user.getOciCfg().getRegion(), user.getArchitecture(), user.getArchitecture());
            return instanceDetailDTO;
        }

        try {
            for (AvailabilityDomain availableDomain : availabilityDomains) {
                try {
                    Vcn vcn = null;
                    InternetGateway internetGateway;
                    Subnet subnet = null;
                    NetworkSecurityGroup networkSecurityGroup = null;
                    LaunchInstanceDetails launchInstanceDetails;
                    Instance instance;
                    List<Vcn> vcnList = listVcn();
                    List<Shape> shapes = getShape(computeClient, compartmentId, availableDomain, user);
                    if (shapes.size() == 0) {
                        continue;
                    }
                    for (Shape shape : shapes) {
                        Image image = getImage(computeClient, compartmentId, shape, user);
                        if (image == null) {
                            continue;
                        }

                        if (vcnList.isEmpty()) {
                            log.info("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] 检测到VCN不存在，正在创建VCN......",
                                    user.getUsername(), user.getOciCfg().getRegion(), user.getArchitecture());
                            String networkCidrBlock = getCidr(virtualNetworkClient, compartmentId);
                            vcn = createVcn(virtualNetworkClient, compartmentId, networkCidrBlock);
                            internetGateway = createInternetGateway(virtualNetworkClient, compartmentId, vcn);
                            addInternetGatewayToDefaultRouteTable(virtualNetworkClient, vcn, internetGateway);
                            subnet = createSubnet(virtualNetworkClient, compartmentId, availableDomain, networkCidrBlock, vcn);
                            if (null == subnet) {
                                continue;
                            }
//                            networkSecurityGroup = createNetworkSecurityGroup(virtualNetworkClient, compartmentId, vcn);
//                            addNetworkSecurityGroupSecurityRules(virtualNetworkClient, networkSecurityGroup, networkCidrBlock);
                        } else {
                            for (Vcn vcnItem : vcnList) {
                                vcn = vcnItem;

                                List<InternetGateway> internetGatewayList = virtualNetworkClient.listInternetGateways(ListInternetGatewaysRequest.builder()
                                        .vcnId(vcn.getId())
                                        .compartmentId(compartmentId)
                                        .build()).getItems();
                                if (CollectionUtil.isEmpty(internetGatewayList)) {
                                    log.info("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] ，检测到 VCN：{} 的 Internet 网关不存在，正在创建 Internet 网关......",
                                            user.getUsername(), user.getOciCfg().getRegion(), user.getArchitecture(), vcn.getDisplayName());
                                    internetGateway = createInternetGateway(virtualNetworkClient, compartmentId, vcn);
                                    addInternetGatewayToDefaultRouteTable(virtualNetworkClient, vcn, internetGateway);
                                }

                                List<Subnet> subnets = listSubnets(vcnItem.getId());
                                if (CollectionUtil.isEmpty(subnets)) {
                                    log.info("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] ，检测到 VCN：{} 的子网不存在，正在创建子网......",
                                            user.getUsername(), user.getOciCfg().getRegion(), user.getArchitecture(), vcn.getDisplayName());
                                    subnet = createSubnet(virtualNetworkClient, compartmentId, availableDomain,
                                            getCidr(virtualNetworkClient, compartmentId), vcnItem);
                                    break;
                                } else {
                                    for (Subnet subnetItem : subnets) {
                                        if (!subnetItem.getProhibitInternetIngress()) {
                                            subnet = subnetItem;
                                            break;
                                        }
                                    }
                                    if (subnet == null) {
                                        log.info("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] ，检测到 VCN：{} 不存在公有子网，正在删除私有子网并创建公有子网......",
                                                user.getUsername(), user.getOciCfg().getRegion(), user.getArchitecture(), vcn.getDisplayName());
                                        subnets.forEach(this::deleteSubnet);
                                        subnet = createSubnet(virtualNetworkClient, compartmentId, availableDomain,
                                                getCidr(virtualNetworkClient, compartmentId), vcn);
                                        break;
                                    }
                                }
                            }
                            log.info("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] ，检测到 VCN：{} 存在，默认使用该 VCN 的公有子网：{} 创建实例......",
                                    user.getUsername(), user.getOciCfg().getRegion(), user.getArchitecture(), vcn.getDisplayName(), subnet.getDisplayName());
                        }

                        String cloudInitScript = CommonUtils.getPwdShell(user.getRootPassword());
                        launchInstanceDetails = createLaunchInstanceDetails(
                                compartmentId, availableDomain,
                                shape, image,
                                subnet, networkSecurityGroup,
                                cloudInitScript, user);
                        instance = createInstance(computeClient.newWaiters(workRequestClient), launchInstanceDetails);
                        printInstance(computeClient, virtualNetworkClient, instance, instanceDetailDTO);

                        log.info("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] 开机成功，正在为实例预配......",
                                user.getUsername(), user.getOciCfg().getRegion(), user.getArchitecture());
                        instanceDetailDTO.setSuccess(true);
                        instanceDetailDTO.setImage(image.getId());
                        instanceDetailDTO.setRegion(user.getOciCfg().getRegion());
                        instanceDetailDTO.setOcpus(user.getOcpus());
                        instanceDetailDTO.setMemory(user.getMemory());
                        instanceDetailDTO.setDisk(user.getDisk());
                        instanceDetailDTO.setRootPassword(user.getRootPassword());
                        instanceDetailDTO.setShape(shape.getShape());
                        return instanceDetailDTO;
                    }
                } catch (Exception e) {
                    if (e instanceof BmcException) {
                        BmcException error = (BmcException) e;
                        if (error.getStatusCode() == 500 ||
                                (error.getMessage().contains(ErrorEnum.CAPACITY.getErrorType()) ||
                                        error.getMessage().contains(ErrorEnum.CAPACITY_HOST.getErrorType()))) {
                            size--;
                            if (size > 0) {
                                log.warn("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] 容量不足，换可用区域继续执行......",
                                        user.getUsername(), user.getOciCfg().getRegion(), user.getArchitecture());
                            } else {
                                log.warn("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] 容量不足，[{}]秒后将重试......",
                                        user.getUsername(), user.getOciCfg().getRegion(), user.getArchitecture(), user.getInterval());
                            }
                        } else if (error.getStatusCode() == 400 && error.getMessage().contains(ErrorEnum.LIMIT_EXCEEDED.getErrorType())) {
                            instanceDetailDTO.setOut(true);
                            log.error("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] 无法创建实例，配额已经超过限制~",
                                    user.getUsername(), user.getOciCfg().getRegion(), user.getArchitecture(), e);
                            return instanceDetailDTO;
                        } else if (error.getStatusCode() == 429 || error.getMessage().contains(ErrorEnum.TOO_MANY_REQUESTS.getErrorType())) {
                            instanceDetailDTO.setTooManyReq(true);
                            log.warn("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] 开机请求频繁，[{}]秒后将重试......",
                                    user.getUsername(), user.getOciCfg().getRegion(), user.getArchitecture(), user.getInterval());
                            return instanceDetailDTO;
                        } else {
//                            instanceDetailDTO.setOut(true);
                            log.error("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] 出现错误了，原因为：{}",
                                    user.getUsername(), user.getOciCfg().getRegion(), user.getArchitecture(),
                                    e.getMessage(), e);
                            return instanceDetailDTO;
                        }
                    } else {
//                        instanceDetailDTO.setOut(true);
                        log.error("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] 出现错误了，原因为：{}",
                                user.getUsername(), user.getOciCfg().getRegion(), user.getArchitecture(),
                                e.getMessage(), e);
                        return instanceDetailDTO;
                    }
                }
            }
        } catch (Exception e) {
//            instanceDetailDTO.setOut(true);
            log.error("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] 出现错误了，原因为：{}",
                    user.getUsername(), user.getOciCfg().getRegion(), user.getArchitecture(),
                    e.getMessage(), e);
            return instanceDetailDTO;
        }
        return instanceDetailDTO;
    }

    public SysUserDTO getUser() {
        return user;
    }

    public boolean checkVcnIsPublic(Vcn vcn) {
        List<Subnet> subnets = listSubnets(vcn.getId());
        if (CollectionUtil.isEmpty(subnets)) {
            return true;
        }
        boolean isPublicSub = true;
        for (Subnet subnetItem : subnets) {
            if (subnetItem.getProhibitInternetIngress()) {
                isPublicSub = false;
                break;
            }
        }
        return isPublicSub;
    }

    public List<com.oracle.bmc.identity.model.Region> listAllRegions() {
        ListRegionsResponse listRegionsResponse = identityClient.listRegions(ListRegionsRequest.builder().build());
        List<com.oracle.bmc.identity.model.Region> regions = listRegionsResponse.getItems();
        return regions.isEmpty() ? Collections.emptyList() : regions;
    }

    public List<RegionSubscription> listRegionSubscriptions() {
        ListRegionSubscriptionsResponse response = identityClient.listRegionSubscriptions(
                ListRegionSubscriptionsRequest.builder()
                        .tenancyId(compartmentId)
                        .build());
        List<RegionSubscription> subscriptionList = response.getItems();
        return subscriptionList.isEmpty() ? Collections.emptyList() : subscriptionList;
    }

    public void deleteAllMfa() {
        ListMfaTotpDevicesResponse listMfaTotpDevicesResponse = identityClient.listMfaTotpDevices(
                ListMfaTotpDevicesRequest.builder()
                        .userId(user.getOciCfg().getUserId())
                        .build());
        List<MfaTotpDeviceSummary> listMfaTotpDevicesResponseItems = listMfaTotpDevicesResponse.getItems();
        if (!listMfaTotpDevicesResponseItems.isEmpty()) {
            listMfaTotpDevicesResponseItems.parallelStream().forEach(item -> {
                identityClient.deleteMfaTotpDevice(DeleteMfaTotpDeviceRequest.builder()
                        .mfaTotpDeviceId(item.getId())
                        .userId(user.getOciCfg().getUserId())
                        .build());
            });
        }
    }

    public void deleteAllApiKey() {
        ListApiKeysResponse listApiKeysResponse = identityClient.listApiKeys(ListApiKeysRequest.builder()
                .userId(user.getOciCfg().getUserId())
                .build());
        List<ApiKey> items = listApiKeysResponse.getItems();
        if (!items.isEmpty()) {
            items.parallelStream().forEach(item -> {
                identityClient.deleteApiKey(DeleteApiKeyRequest.builder()
                        .userId(user.getOciCfg().getUserId())
                        .fingerprint(item.getFingerprint())
                        .build());
            });
        }
    }

    public User getUserInfo() {
        return identityClient.getUser(GetUserRequest.builder()
                .userId(user.getOciCfg().getUserId())
                .build()).getUser();
    }

    public void updateUser(String email, String dbUserName, String description) {
        identityClient.updateUser(UpdateUserRequest.builder()
                .userId(user.getOciCfg().getUserId())
                .updateUserDetails(UpdateUserDetails.builder()
                        .email(email)
                        .dbUserName(dbUserName)
                        .description(description)
                        .build())
                .build());
    }

    public String createOrResetUIPassword() {
        CreateOrResetUIPasswordResponse uIPassword = identityClient.createOrResetUIPassword(
                CreateOrResetUIPasswordRequest.builder()
                        .userId(user.getOciCfg().getUserId())
                        .build());
        String password = uIPassword.getUIPassword().getPassword();
        log.info("用户：[{}] ，区域：[{}] 成功创建/重置登录密码，新密码：【 {} 】",
                user.getUsername(), user.getOciCfg().getRegion(), password);
        return password;
    }

    public List<Instance> listInstances() {
        ListInstancesRequest request = ListInstancesRequest.builder()
                .compartmentId(user.getOciCfg().getTenantId())
                .build();
        ListInstancesResponse response = computeClient.listInstances(request);
        List<Instance> instanceList = response.getItems();
        return CollectionUtil.isEmpty(instanceList) ? Collections.emptyList() : instanceList.parallelStream()
                .filter(x -> !x.getLifecycleState().getValue().equals(InstanceStateEnum.LIFECYCLE_STATE_TERMINATED.getState()))
                .collect(Collectors.toList());
    }

    public Instance getInstanceById(String instanceId) {
        GetInstanceResponse instance = computeClient.getInstance(GetInstanceRequest.builder()
                .instanceId(instanceId)
                .build());
        return instance.getInstance();
    }

    public List<Vnic> listInstanceIPs(String instanceId) {
        // 获取实例的所有VNIC附件
        ListVnicAttachmentsRequest vnicRequest = ListVnicAttachmentsRequest.builder()
                .compartmentId(compartmentId)
                .instanceId(instanceId)
                .build();

        ListVnicAttachmentsResponse vnicResponse = computeClient.listVnicAttachments(vnicRequest);
        List<VnicAttachment> vnicAttachments = vnicResponse.getItems();
        return vnicAttachments.parallelStream()
                .filter(x -> x.getLifecycleState().equals(VnicAttachment.LifecycleState.Attached))
                .map(x -> {
                    String vnicId = x.getVnicId();
                    if (vnicId != null) {
                        // 获取VNIC详细信息，包括IP地址
                        GetVnicRequest getVnicRequest = GetVnicRequest.builder().vnicId(vnicId).build();
                        GetVnicResponse getVnicResponse = virtualNetworkClient.getVnic(getVnicRequest);
                        Vnic vnic = getVnicResponse.getVnic();
                        return vnic;
                    }
                    return null;
                }).collect(Collectors.toList());
    }

    private String getCidr(VirtualNetworkClient virtualNetworkClient, String compartmentId) {
        // 创建列出 VCN 的请求
        ListVcnsRequest listVcnsRequest = ListVcnsRequest.builder()
                .compartmentId(compartmentId)
                .build();

        // 发送请求并获取响应
        ListVcnsResponse listVcnsResponse = virtualNetworkClient.listVcns(listVcnsRequest);
        if (CollectionUtil.isEmpty(listVcnsResponse.getItems())) {
            return CIDR_BLOCK;
        }
        return listVcnsResponse.getItems().get(0).getCidrBlock();
    }

    public List<AvailabilityDomain> getAvailabilityDomains(
            IdentityClient identityClient, String compartmentId) {
        ListAvailabilityDomainsResponse listAvailabilityDomainsResponse =
                identityClient.listAvailabilityDomains(ListAvailabilityDomainsRequest.builder()
                        .compartmentId(compartmentId)
                        .build());
        List<AvailabilityDomain> availabilityDomainList = listAvailabilityDomainsResponse.getItems();
        if (availabilityDomainList.isEmpty()) {
            log.error("用户：[{}] ，区域：[{}] ，可用域不足", user.getUsername(), user.getOciCfg().getRegion());
            throw new OciException(-1, "可用域不足");
        }
        return availabilityDomainList;
    }

    public List<AvailabilityDomain> getAvailabilityDomains() {
        ListAvailabilityDomainsResponse listAvailabilityDomainsResponse =
                identityClient.listAvailabilityDomains(ListAvailabilityDomainsRequest.builder()
                        .compartmentId(compartmentId)
                        .build());
        List<AvailabilityDomain> availabilityDomainList = listAvailabilityDomainsResponse.getItems();
        if (availabilityDomainList.isEmpty()) {
            log.error("用户：[{}] ，区域：[{}] ，可用域不足", user.getUsername(), user.getOciCfg().getRegion());
            throw new OciException(-1, "可用域不足");
        }
        return availabilityDomainList;
    }

    private List<Shape> getShape(
            ComputeClient computeClient,
            String compartmentId,
            AvailabilityDomain availabilityDomain,
            SysUserDTO user) {
        ListShapesResponse listShapesResponse = computeClient.listShapes(ListShapesRequest.builder()
                .availabilityDomain(availabilityDomain.getName())
                .compartmentId(compartmentId)
                .build());
        List<Shape> shapes = listShapesResponse.getItems();
        List<Shape> vmShapes = shapes.isEmpty() ? Collections.emptyList() : shapes.stream()
                .filter(shape -> shape.getShape().startsWith("VM"))
                .collect(Collectors.toList());
        List<Shape> shapesNewList = new ArrayList<>();
        ArchitectureEnum type = ArchitectureEnum.getType(user.getArchitecture());
        if (!vmShapes.isEmpty()) {
            for (Shape vmShape : vmShapes) {
                if (type.getShapeDetail().equals(vmShape.getShape())) {
                    shapesNewList.add(vmShape);
                }
            }
        }
        return shapesNewList;
    }

    private Image getImage(ComputeClient computeClient, String compartmentId, Shape shape, SysUserDTO user) {
        OperationSystemEnum systemType = OperationSystemEnum.getSystemType(user.getOperationSystem());
        ListImagesRequest listImagesRequest =
                ListImagesRequest.builder()
                        .shape(shape.getShape())
                        .compartmentId(compartmentId)
                        .operatingSystem(systemType.getType())
                        .operatingSystemVersion(systemType.getVersion())
                        .build();
        ListImagesResponse response = computeClient.listImages(listImagesRequest);
        List<Image> images = response.getItems();
        if (images.isEmpty()) {
            return null;
        }

        // For demonstration, we just return the first image but for Production code you should have
        // a better
        // way of determining what is needed.
        //
        // Note the latest version of the images for the same operating system is returned firstly.
        return images.get(0);
    }

    synchronized private Vcn createVcn(VirtualNetworkClient virtualNetworkClient, String compartmentId, String cidrBlock)
            throws Exception {
        String vcnName = "oci-helper-vcn";
        CreateVcnDetails createVcnDetails = CreateVcnDetails.builder()
                .cidrBlock(cidrBlock)
                .isIpv6Enabled(true)
                .compartmentId(compartmentId)
                .displayName(vcnName)
                .build();
        CreateVcnRequest createVcnRequest = CreateVcnRequest.builder().createVcnDetails(createVcnDetails).build();
        CreateVcnResponse createVcnResponse = virtualNetworkClient.createVcn(createVcnRequest);

        GetVcnRequest getVcnRequest = GetVcnRequest.builder().vcnId(createVcnResponse.getVcn().getId()).build();
        GetVcnResponse getVcnResponse = virtualNetworkClient
                .getWaiters()
                .forVcn(getVcnRequest, Vcn.LifecycleState.Available)
                .execute();
        return getVcnResponse.getVcn();
    }

    public Vcn createVcn(String cidrBlock)
            throws Exception {
        String vcnName = "oci-helper-vcn";
        CreateVcnDetails createVcnDetails = CreateVcnDetails.builder()
                .cidrBlock(cidrBlock)
                .isIpv6Enabled(true)
                .compartmentId(compartmentId)
                .displayName(vcnName)
                .build();
        CreateVcnRequest createVcnRequest = CreateVcnRequest.builder().createVcnDetails(createVcnDetails).build();
        CreateVcnResponse createVcnResponse = virtualNetworkClient.createVcn(createVcnRequest);

        GetVcnRequest getVcnRequest = GetVcnRequest.builder().vcnId(createVcnResponse.getVcn().getId()).build();
        GetVcnResponse getVcnResponse = virtualNetworkClient
                .getWaiters()
                .forVcn(getVcnRequest, Vcn.LifecycleState.Available)
                .execute();
        return getVcnResponse.getVcn();
    }

    private void deleteVcn(VirtualNetworkClient virtualNetworkClient, Vcn vcn) throws Exception {
        DeleteVcnRequest deleteVcnRequest = DeleteVcnRequest.builder().vcnId(vcn.getId()).build();
        virtualNetworkClient.deleteVcn(deleteVcnRequest);

        GetVcnRequest getVcnRequest = GetVcnRequest.builder().vcnId(vcn.getId()).build();
        virtualNetworkClient
                .getWaiters()
                .forVcn(getVcnRequest, Vcn.LifecycleState.Terminated)
                .execute();
    }

    public List<Vcn> listVcn() {
        ListVcnsRequest request = ListVcnsRequest.builder()
                .compartmentId(compartmentId)
                .lifecycleState(Vcn.LifecycleState.Available)
                .build();
        ListVcnsResponse response = virtualNetworkClient.listVcns(request);
        return response.getItems();
    }

    public Vcn getVcnByInstanceId(String instanceId) {
        Vnic vnic = getVnicByInstanceId(instanceId);
        // 获取子网信息
        String subnetId = vnic.getSubnetId();
        GetSubnetRequest getSubnetRequest = GetSubnetRequest.builder().subnetId(subnetId).build();
        GetSubnetResponse getSubnetResponse = virtualNetworkClient.getSubnet(getSubnetRequest);
        Subnet subnet = getSubnetResponse.getSubnet();
        // 获取 VCN 信息
        String vcnId = subnet.getVcnId();
        GetVcnRequest getVcnRequest = GetVcnRequest.builder().vcnId(vcnId).build();
        GetVcnResponse getVcnResponse = virtualNetworkClient.getVcn(getVcnRequest);
        return getVcnResponse.getVcn();
    }

    public void deleteVcnById(String vcnId) {
        virtualNetworkClient.updateRouteTable(UpdateRouteTableRequest.builder()
                .rtId(getVcnById(vcnId).getDefaultRouteTableId())
                .updateRouteTableDetails(UpdateRouteTableDetails.builder()
                        .routeRules(Collections.emptyList())
                        .build())
                .build());
        deleteAllSubnets(vcnId);
        deleteAllInternetGateways(vcnId);
        virtualNetworkClient.deleteVcn(DeleteVcnRequest.builder()
                .vcnId(vcnId)
                .build());
    }

    private void deleteAllSubnets(String vcnId) {
        ListSubnetsResponse subnetsResponse = virtualNetworkClient.listSubnets(
                ListSubnetsRequest.builder()
                        .vcnId(vcnId)
                        .compartmentId(compartmentId)
                        .build()
        );
        if (subnetsResponse.getItems().isEmpty()) {
            return;
        }
        subnetsResponse.getItems().forEach(subnet -> virtualNetworkClient.deleteSubnet(
                DeleteSubnetRequest.builder().subnetId(subnet.getId()).build()));
    }

    private void deleteAllInternetGateways(String vcnId) {
        ListInternetGatewaysResponse response = virtualNetworkClient.listInternetGateways(
                ListInternetGatewaysRequest.builder()
                        .vcnId(vcnId)
                        .compartmentId(compartmentId)
                        .build()
        );
        if (response.getItems().isEmpty()) {
            return;
        }
        response.getItems().forEach(ig -> virtualNetworkClient.deleteInternetGateway(
                DeleteInternetGatewayRequest.builder().igId(ig.getId()).build()));
    }

    synchronized private InternetGateway createInternetGateway(
            VirtualNetworkClient virtualNetworkClient, String compartmentId, Vcn vcn)
            throws Exception {
        String internetGatewayName = "oci-helper-gateway";

        //查询网关是否存在,不存在再创建
        ListInternetGatewaysRequest build = ListInternetGatewaysRequest.builder()
                .compartmentId(compartmentId)
                .displayName(internetGatewayName)
                .build();

        ListInternetGatewaysResponse listInternetGatewaysResponse = virtualNetworkClient.listInternetGateways(build);
        if (listInternetGatewaysResponse.getItems().size() > 0) {
            return listInternetGatewaysResponse.getItems().get(0);
        }

        CreateInternetGatewayDetails createInternetGatewayDetails = CreateInternetGatewayDetails.builder()
                .compartmentId(compartmentId)
                .displayName(internetGatewayName)
                .isEnabled(true)
                .vcnId(vcn.getId())
                .build();
        CreateInternetGatewayRequest createInternetGatewayRequest = CreateInternetGatewayRequest.builder()
                .createInternetGatewayDetails(createInternetGatewayDetails)
                .build();
        CreateInternetGatewayResponse createInternetGatewayResponse = virtualNetworkClient
                .createInternetGateway(createInternetGatewayRequest);

        GetInternetGatewayRequest getInternetGatewayRequest = GetInternetGatewayRequest.builder()
                .igId(createInternetGatewayResponse.getInternetGateway().getId())
                .build();
        GetInternetGatewayResponse getInternetGatewayResponse = virtualNetworkClient
                .getWaiters()
                .forInternetGateway(getInternetGatewayRequest, InternetGateway.LifecycleState.Available)
                .execute();
        return getInternetGatewayResponse.getInternetGateway();
    }

    private void deleteInternetGateway(VirtualNetworkClient virtualNetworkClient, InternetGateway internetGateway)
            throws Exception {
        DeleteInternetGatewayRequest deleteInternetGatewayRequest = DeleteInternetGatewayRequest.builder()
                .igId(internetGateway.getId())
                .build();
        virtualNetworkClient.deleteInternetGateway(deleteInternetGatewayRequest);
        GetInternetGatewayRequest getInternetGatewayRequest = GetInternetGatewayRequest.builder()
                .igId(internetGateway.getId())
                .build();
        virtualNetworkClient.getWaiters()
                .forInternetGateway(getInternetGatewayRequest, InternetGateway.LifecycleState.Terminated)
                .execute();
    }

    synchronized private void addInternetGatewayToDefaultRouteTable(
            VirtualNetworkClient virtualNetworkClient,
            Vcn vcn, InternetGateway internetGateway) throws Exception {
        GetRouteTableRequest getRouteTableRequest = GetRouteTableRequest.builder()
                .rtId(vcn.getDefaultRouteTableId())
                .build();
        GetRouteTableResponse getRouteTableResponse = virtualNetworkClient.getRouteTable(getRouteTableRequest);
        List<RouteRule> routeRules = getRouteTableResponse.getRouteTable().getRouteRules();

        // 检查是否已有相同的路由规则
        boolean ruleExists = routeRules.stream()
                .anyMatch(rule -> "0.0.0.0/0".equals(rule.getDestination())
                        && rule.getDestinationType() == RouteRule.DestinationType.CidrBlock);

        if (ruleExists) {
            log.info("The route rule for destination 0.0.0.0/0 already exists.");
            return; // 退出方法，不添加新的规则
        }

        // 创建新的路由规则
        RouteRule internetAccessRoute = RouteRule.builder()
                .destination("0.0.0.0/0")
                .destinationType(RouteRule.DestinationType.CidrBlock)
                .networkEntityId(internetGateway.getId())
                .build();

        // 将新的规则添加到新的列表中
        List<RouteRule> updatedRouteRules = new ArrayList<>(routeRules);
        updatedRouteRules.add(internetAccessRoute);

        UpdateRouteTableDetails updateRouteTableDetails = UpdateRouteTableDetails.builder()
                .routeRules(updatedRouteRules)
                .build();
        UpdateRouteTableRequest updateRouteTableRequest = UpdateRouteTableRequest.builder()
                .updateRouteTableDetails(updateRouteTableDetails)
                .rtId(vcn.getDefaultRouteTableId())
                .build();

        virtualNetworkClient.updateRouteTable(updateRouteTableRequest);

        // 等待路由表更新完成
        getRouteTableResponse = virtualNetworkClient.getWaiters()
                .forRouteTable(getRouteTableRequest, RouteTable.LifecycleState.Available)
                .execute();
        routeRules = getRouteTableResponse.getRouteTable().getRouteRules();
    }

    private void clearRouteRulesFromDefaultRouteTable(
            VirtualNetworkClient virtualNetworkClient, Vcn vcn) throws Exception {
        List<RouteRule> routeRules = new ArrayList<>();
        UpdateRouteTableDetails updateRouteTableDetails =
                UpdateRouteTableDetails.builder().routeRules(routeRules).build();
        UpdateRouteTableRequest updateRouteTableRequest =
                UpdateRouteTableRequest.builder()
                        .updateRouteTableDetails(updateRouteTableDetails)
                        .rtId(vcn.getDefaultRouteTableId())
                        .build();
        virtualNetworkClient.updateRouteTable(updateRouteTableRequest);

        GetRouteTableRequest getRouteTableRequest =
                GetRouteTableRequest.builder().rtId(vcn.getDefaultRouteTableId()).build();
        virtualNetworkClient
                .getWaiters()
                .forRouteTable(getRouteTableRequest, RouteTable.LifecycleState.Available)
                .execute();
        if (log.isDebugEnabled()) {
            System.out.println("Cleared route rules from route table: " + vcn.getDefaultRouteTableId());
            System.out.println();
        }

    }

    synchronized private Subnet createSubnet(
            VirtualNetworkClient virtualNetworkClient,
            String compartmentId,
            AvailabilityDomain availabilityDomain,
            String networkCidrBlock,
            Vcn vcn)
            throws Exception {
        String subnetName = "oci-helper-subnet";
        Subnet subnet;
        CreateSubnetDetails createSubnetDetails =
                CreateSubnetDetails.builder()
                        .compartmentId(compartmentId)
                        .displayName(subnetName)
                        .cidrBlock(networkCidrBlock)
                        .vcnId(vcn.getId())
                        .routeTableId(vcn.getDefaultRouteTableId())
                        .build();
        CreateSubnetRequest createSubnetRequest =
                CreateSubnetRequest.builder().createSubnetDetails(createSubnetDetails).build();
        CreateSubnetResponse createSubnetResponse =
                virtualNetworkClient.createSubnet(createSubnetRequest);

        GetSubnetRequest getSubnetRequest =
                GetSubnetRequest.builder()
                        .subnetId(createSubnetResponse.getSubnet().getId())
                        .build();
        GetSubnetResponse getSubnetResponse =
                virtualNetworkClient
                        .getWaiters()
                        .forSubnet(getSubnetRequest, Subnet.LifecycleState.Available)
                        .execute();
        subnet = getSubnetResponse.getSubnet();
        return subnet;
    }

    private void deleteSubnet(Subnet subnet) {
        try {
            DeleteSubnetRequest deleteSubnetRequest = DeleteSubnetRequest.builder().subnetId(subnet.getId()).build();
            virtualNetworkClient.deleteSubnet(deleteSubnetRequest);

            GetSubnetRequest getSubnetRequest = GetSubnetRequest.builder().subnetId(subnet.getId()).build();
            virtualNetworkClient
                    .getWaiters()
                    .forSubnet(getSubnetRequest, Subnet.LifecycleState.Terminated)
                    .execute();

            log.info("Deleted Subnet: [{}]", subnet.getId());
        } catch (Exception e) {
            log.error("delete subnet fail error", e);
        }
    }

    public List<Subnet> listSubnets(String vcnId) {
        ListSubnetsRequest request = ListSubnetsRequest.builder()
                .compartmentId(compartmentId)
                .vcnId(vcnId)
                .build();

        ListSubnetsResponse response = virtualNetworkClient.listSubnets(request);

//        for (Subnet subnet : response.getItems()) {
//            System.out.println("Subnet Name: " + subnet.getDisplayName() + ", OCID: " + subnet.getId());
//        }
        return response.getItems();
    }

    private NetworkSecurityGroup createNetworkSecurityGroup(
            VirtualNetworkClient virtualNetworkClient, String compartmentId, Vcn vcn)
            throws Exception {
        String networkSecurityGroupName = System.currentTimeMillis() + "-nsg";

        CreateNetworkSecurityGroupDetails createNetworkSecurityGroupDetails =
                CreateNetworkSecurityGroupDetails.builder()
                        .compartmentId(compartmentId)
                        .displayName(networkSecurityGroupName)
                        .vcnId(vcn.getId())
                        .build();
        CreateNetworkSecurityGroupRequest createNetworkSecurityGroupRequest =
                CreateNetworkSecurityGroupRequest.builder()
                        .createNetworkSecurityGroupDetails(createNetworkSecurityGroupDetails)
                        .build();

        ListNetworkSecurityGroupsRequest build = ListNetworkSecurityGroupsRequest.builder().
                compartmentId(compartmentId).
                displayName(networkSecurityGroupName).vcnId(vcn.getId()).build();

        ListNetworkSecurityGroupsResponse listNetworkSecurityGroupsResponse = virtualNetworkClient.listNetworkSecurityGroups(build);
        if (listNetworkSecurityGroupsResponse.getItems().size() > 0) {
            return listNetworkSecurityGroupsResponse.getItems().get(0);
        }

        CreateNetworkSecurityGroupResponse createNetworkSecurityGroupResponse =
                virtualNetworkClient.createNetworkSecurityGroup(createNetworkSecurityGroupRequest);

        GetNetworkSecurityGroupRequest getNetworkSecurityGroupRequest =
                GetNetworkSecurityGroupRequest.builder()
                        .networkSecurityGroupId(
                                createNetworkSecurityGroupResponse
                                        .getNetworkSecurityGroup()
                                        .getId())
                        .build();
        GetNetworkSecurityGroupResponse getNetworkSecurityGroupResponse =
                virtualNetworkClient
                        .getWaiters()
                        .forNetworkSecurityGroup(
                                getNetworkSecurityGroupRequest,
                                NetworkSecurityGroup.LifecycleState.Available)
                        .execute();
        NetworkSecurityGroup networkSecurityGroup =
                getNetworkSecurityGroupResponse.getNetworkSecurityGroup();

        if (log.isDebugEnabled()) {
            System.out.println("Created Network Security Group: " + networkSecurityGroup.getId());
            System.out.println(networkSecurityGroup);
            System.out.println();
        }

        return networkSecurityGroup;
    }

    private void deleteNetworkSecurityGroup(
            VirtualNetworkClient virtualNetworkClient, NetworkSecurityGroup networkSecurityGroup)
            throws Exception {
        DeleteNetworkSecurityGroupRequest deleteNetworkSecurityGroupRequest =
                DeleteNetworkSecurityGroupRequest.builder()
                        .networkSecurityGroupId(networkSecurityGroup.getId())
                        .build();
        virtualNetworkClient.deleteNetworkSecurityGroup(deleteNetworkSecurityGroupRequest);

        GetNetworkSecurityGroupRequest getNetworkSecurityGroupRequest =
                GetNetworkSecurityGroupRequest.builder()
                        .networkSecurityGroupId(networkSecurityGroup.getId())
                        .build();
        virtualNetworkClient
                .getWaiters()
                .forNetworkSecurityGroup(
                        getNetworkSecurityGroupRequest,
                        NetworkSecurityGroup.LifecycleState.Terminated)
                .execute();

        if (log.isDebugEnabled()) {
            System.out.println("Deleted Network Security Group: " + networkSecurityGroup.getId());
            System.out.println();
        }

    }

    public List<NetworkSecurityGroup> listNetworkSecurityGroups() {
        ListNetworkSecurityGroupsRequest request = ListNetworkSecurityGroupsRequest.builder()
                .compartmentId(compartmentId)
                .build();

        ListNetworkSecurityGroupsResponse response = virtualNetworkClient.listNetworkSecurityGroups(request);

//        for (NetworkSecurityGroup nsg : response.getItems()) {
//            System.out.println("NetworkSecurityGroup Name: " + nsg.getDisplayName() + ", OCID: " + nsg.getId());
//        }
        return response.getItems();
    }

    private void addNetworkSecurityGroupSecurityRules(
            VirtualNetworkClient virtualNetworkClient,
            NetworkSecurityGroup networkSecurityGroup,
            String networkCidrBlock) {
        ListNetworkSecurityGroupSecurityRulesRequest listNetworkSecurityGroupSecurityRulesRequest =
                ListNetworkSecurityGroupSecurityRulesRequest.builder()
                        .networkSecurityGroupId(networkSecurityGroup.getId())
                        .build();
        ListNetworkSecurityGroupSecurityRulesResponse
                listNetworkSecurityGroupSecurityRulesResponse =
                virtualNetworkClient.listNetworkSecurityGroupSecurityRules(
                        listNetworkSecurityGroupSecurityRulesRequest);
        List<SecurityRule> securityRules = listNetworkSecurityGroupSecurityRulesResponse.getItems();

        if (log.isDebugEnabled()) {
            System.out.println("Current Security Rules in Network Security Group");
            System.out.println("================================================");
            securityRules.forEach(System.out::println);
            System.out.println();
        }

        AddSecurityRuleDetails addSecurityRuleDetails =
                AddSecurityRuleDetails.builder()
                        .description("Incoming HTTP connections")
                        .direction(AddSecurityRuleDetails.Direction.Ingress)
                        .protocol("6")
                        .source(networkCidrBlock)
                        .sourceType(AddSecurityRuleDetails.SourceType.CidrBlock)
                        .tcpOptions(
                                TcpOptions.builder()
                                        .destinationPortRange(
                                                PortRange.builder().min(80).max(80).build())
                                        .build())
                        .build();
        AddNetworkSecurityGroupSecurityRulesDetails addNetworkSecurityGroupSecurityRulesDetails =
                AddNetworkSecurityGroupSecurityRulesDetails.builder()
                        .securityRules(Arrays.asList(addSecurityRuleDetails))
                        .build();
        AddNetworkSecurityGroupSecurityRulesRequest addNetworkSecurityGroupSecurityRulesRequest =
                AddNetworkSecurityGroupSecurityRulesRequest.builder()
                        .networkSecurityGroupId(networkSecurityGroup.getId())
                        .addNetworkSecurityGroupSecurityRulesDetails(
                                addNetworkSecurityGroupSecurityRulesDetails)
                        .build();
        virtualNetworkClient.addNetworkSecurityGroupSecurityRules(
                addNetworkSecurityGroupSecurityRulesRequest);

        listNetworkSecurityGroupSecurityRulesResponse =
                virtualNetworkClient.listNetworkSecurityGroupSecurityRules(
                        listNetworkSecurityGroupSecurityRulesRequest);
        securityRules = listNetworkSecurityGroupSecurityRulesResponse.getItems();

        if (log.isDebugEnabled()) {
            System.out.println("Updated Security Rules in Network Security Group");
            System.out.println("================================================");
            securityRules.forEach(System.out::println);
            System.out.println();
        }

    }

    private void clearNetworkSecurityGroupSecurityRules(
            VirtualNetworkClient virtualNetworkClient, NetworkSecurityGroup networkSecurityGroup) {
        ListNetworkSecurityGroupSecurityRulesRequest listNetworkSecurityGroupSecurityRulesRequest =
                ListNetworkSecurityGroupSecurityRulesRequest.builder()
                        .networkSecurityGroupId(networkSecurityGroup.getId())
                        .build();
        ListNetworkSecurityGroupSecurityRulesResponse
                listNetworkSecurityGroupSecurityRulesResponse =
                virtualNetworkClient.listNetworkSecurityGroupSecurityRules(
                        listNetworkSecurityGroupSecurityRulesRequest);
        List<SecurityRule> securityRules = listNetworkSecurityGroupSecurityRulesResponse.getItems();

        List<String> securityRuleIds =
                securityRules.stream().map(SecurityRule::getId).collect(Collectors.toList());
        RemoveNetworkSecurityGroupSecurityRulesDetails
                removeNetworkSecurityGroupSecurityRulesDetails =
                RemoveNetworkSecurityGroupSecurityRulesDetails.builder()
                        .securityRuleIds(securityRuleIds)
                        .build();
        RemoveNetworkSecurityGroupSecurityRulesRequest
                removeNetworkSecurityGroupSecurityRulesRequest =
                RemoveNetworkSecurityGroupSecurityRulesRequest.builder()
                        .networkSecurityGroupId(networkSecurityGroup.getId())
                        .removeNetworkSecurityGroupSecurityRulesDetails(
                                removeNetworkSecurityGroupSecurityRulesDetails)
                        .build();
        virtualNetworkClient.removeNetworkSecurityGroupSecurityRules(
                removeNetworkSecurityGroupSecurityRulesRequest);

        System.out.println(
                "Removed all Security Rules in Network Security Group: "
                        + networkSecurityGroup.getId());
        System.out.println();
    }

    synchronized private Instance createInstance(ComputeWaiters computeWaiters, LaunchInstanceDetails launchInstanceDetails)
            throws Exception {
        LaunchInstanceRequest launchInstanceRequest = LaunchInstanceRequest.builder()
                .launchInstanceDetails(launchInstanceDetails)
                .build();
        LaunchInstanceResponse launchInstanceResponse = computeWaiters
                .forLaunchInstance(launchInstanceRequest)
                .execute();
        GetInstanceRequest getInstanceRequest = GetInstanceRequest.builder()
                .instanceId(launchInstanceResponse.getInstance().getId())
                .build();
        GetInstanceResponse getInstanceResponse = computeWaiters
                .forInstance(getInstanceRequest, Instance.LifecycleState.Running)
                .execute();
        Instance instance = getInstanceResponse.getInstance();

//        System.out.println("Launched Instance: " + instance.getId());
//        System.out.println(instance);
//        System.out.println();
        return instance;
    }

    private LaunchInstanceDetails createLaunchInstanceDetails(
            String compartmentId,
            AvailabilityDomain availabilityDomain,
            Shape shape,
            Image image,
            Subnet subnet,
            NetworkSecurityGroup networkSecurityGroup,
            String script,
            SysUserDTO user) {
        String instanceName = "instance-" + LocalDateTime.now().format(CommonUtils.DATETIME_FMT_PURE);
        String encodedCloudInitScript = Base64.getEncoder().encodeToString(script.getBytes());
//        Map<String, Object> extendedMetadata = new HashMap<>();
        /*extendedMetadata.put(
                "java-sdk-example-extended-metadata-key",
                "java-sdk-example-extended-metadata-value");
        extendedMetadata = Collections.unmodifiableMap(extendedMetadata);*/
        InstanceSourceViaImageDetails instanceSourceViaImageDetails = InstanceSourceViaImageDetails.builder()
                .imageId(image.getId())
                //.kmsKeyId((kmsKeyId == null || "".equals(kmsKeyId)) ? null : kmsKeyId)
                .build();
        CreateVnicDetails createVnicDetails = CreateVnicDetails.builder()
                .subnetId(subnet.getId())
                .nsgIds(networkSecurityGroup == null ? null : Arrays.asList(networkSecurityGroup.getId()))
                .build();
        LaunchInstanceAgentConfigDetails launchInstanceAgentConfigDetails = LaunchInstanceAgentConfigDetails.builder()
                .isMonitoringDisabled(true)
                .build();
        return LaunchInstanceDetails.builder()
                .availabilityDomain(availabilityDomain.getName())
                .compartmentId(compartmentId)
                .displayName(instanceName)
                // faultDomain is optional parameter
//                .faultDomain("FAULT-DOMAIN-2")
                .sourceDetails(instanceSourceViaImageDetails)
                .metadata(Collections.singletonMap("user_data", encodedCloudInitScript))
//                .extendedMetadata(extendedMetadata)
                .shape(shape.getShape())
                .createVnicDetails(createVnicDetails)
                // agentConfig is an optional parameter
                .agentConfig(launchInstanceAgentConfigDetails)
                //配置核心和内存
                .shapeConfig(LaunchInstanceShapeConfigDetails.
                        builder().
                        ocpus(user.getOcpus()).
                        memoryInGBs(user.getMemory()).
                        build())
                //配置磁盘大小
                .sourceDetails(InstanceSourceViaImageDetails.builder()
                        .imageId(image.getId())
                        .bootVolumeSizeInGBs(user.getDisk())
                        .build())
                .build();
    }

    private void printInstance(
            ComputeClient computeClient,
            VirtualNetworkClient virtualNetworkClient,
            Instance instance,
            InstanceDetailDTO instanceDetailDTO) {
        ListVnicAttachmentsRequest listVnicAttachmentsRequest = ListVnicAttachmentsRequest.builder()
                .compartmentId(instance.getCompartmentId())
                .instanceId(instance.getId())
                .build();
        ListVnicAttachmentsResponse listVnicAttachmentsResponse = computeClient.listVnicAttachments(listVnicAttachmentsRequest);
        List<VnicAttachment> vnicAttachments = listVnicAttachmentsResponse.getItems();
        VnicAttachment vnicAttachment = vnicAttachments.get(0);

        GetVnicRequest getVnicRequest = GetVnicRequest.builder()
                .vnicId(vnicAttachment.getVnicId())
                .build();
        GetVnicResponse getVnicResponse = virtualNetworkClient.getVnic(getVnicRequest);
        Vnic vnic = getVnicResponse.getVnic();

        instanceDetailDTO.setPublicIp(vnic.getPublicIp());
    }

    public BootVolume createBootVolume(
            BlockstorageClient blockstorageClient,
            String compartmentId,
            AvailabilityDomain availabilityDomain,
            Image image,
            String kmsKeyId) throws Exception {
        String bootVolumeName = "oci-helper-boot-volume";
        // find existing boot volume by image
        ListBootVolumesRequest listBootVolumesRequest = ListBootVolumesRequest.builder()
                .availabilityDomain(availabilityDomain.getName())
                .compartmentId(compartmentId)
                .build();
        ListBootVolumesResponse listBootVolumesResponse = blockstorageClient.listBootVolumes(listBootVolumesRequest);
        List<BootVolume> bootVolumes = listBootVolumesResponse.getItems();
        String bootVolumeId = null;
        for (BootVolume bootVolume : bootVolumes) {
            if (BootVolume.LifecycleState.Available.equals(bootVolume.getLifecycleState())
                    && image.getId().equals(bootVolume.getImageId())) {
                bootVolumeId = bootVolume.getId();
                break;
            }
        }

        // create a new boot volume based on existing one
        BootVolumeSourceDetails bootVolumeSourceDetails = BootVolumeSourceFromBootVolumeDetails.builder()
                .id(bootVolumeId)
                .build();
        CreateBootVolumeDetails details = CreateBootVolumeDetails.builder()
                .availabilityDomain(availabilityDomain.getName())
                .compartmentId(compartmentId)
                .displayName(bootVolumeName)
                .sourceDetails(bootVolumeSourceDetails)
                .kmsKeyId(kmsKeyId)
                .build();
        CreateBootVolumeRequest createBootVolumeRequest = CreateBootVolumeRequest.builder()
                .createBootVolumeDetails(details)
                .build();
        CreateBootVolumeResponse createBootVolumeResponse = blockstorageClient.createBootVolume(createBootVolumeRequest);

        // wait for boot volume to be ready
        GetBootVolumeRequest getBootVolumeRequest = GetBootVolumeRequest.builder()
                .bootVolumeId(createBootVolumeResponse.getBootVolume().getId())
                .build();
        GetBootVolumeResponse getBootVolumeResponse = blockstorageClient
                .getWaiters()
                .forBootVolume(getBootVolumeRequest, BootVolume.LifecycleState.Available)
                .execute();
        return getBootVolumeResponse.getBootVolume();
    }

    public LaunchInstanceDetails createLaunchInstanceDetailsFromBootVolume(
            LaunchInstanceDetails launchInstanceDetails,
            BootVolume bootVolume) throws Exception {
        String bootVolumeName = "oci-helper-instance-from-boot-volume";
        InstanceSourceViaBootVolumeDetails instanceSourceViaBootVolumeDetails = InstanceSourceViaBootVolumeDetails.builder()
                .bootVolumeId(bootVolume.getId())
                .build();
        LaunchInstanceAgentConfigDetails launchInstanceAgentConfigDetails = LaunchInstanceAgentConfigDetails.builder()
                .isMonitoringDisabled(true)
                .build();
        return LaunchInstanceDetails.builder()
                .copy(launchInstanceDetails)
                .sourceDetails(instanceSourceViaBootVolumeDetails)
                .agentConfig(launchInstanceAgentConfigDetails)
                .build();
    }

    private String getCompartmentIdFromCache() {
        CustomExpiryGuavaCache cache = SpringUtil.getBean(CustomExpiryGuavaCache.class);
        Object compartmentIdInCache = cache.get(CacheConstant.PREFIX_TENANT_COMPARTMENT_ID + getUser().getOciCfg().getTenantId());
        return compartmentIdInCache == null ? findRootCompartment(identityClient, getUser().getOciCfg().getTenantId()) : String.valueOf(compartmentIdInCache);
    }

    private String getRegionFromCache() {
        CustomExpiryGuavaCache cache = SpringUtil.getBean(CustomExpiryGuavaCache.class);
        Object regionInCache = cache.get(CacheConstant.PREFIX_TENANT_REGION + getUser().getOciCfg().getTenantId());
        return regionInCache == null ? getUser().getOciCfg().getRegion() : String.valueOf(regionInCache);
    }

    private String findRootCompartment(IdentityClient identityClient, String tenantId) {
        // 使用`compartmentIdInSubtree`参数来获取所有子区间
        ListCompartmentsRequest request = ListCompartmentsRequest.builder()
                .compartmentId(tenantId)
                .compartmentIdInSubtree(true)
                .accessLevel(ListCompartmentsRequest.AccessLevel.Accessible)
                .build();

        try {
            ListCompartmentsResponse response = identityClient.listCompartments(request);
            List<Compartment> compartments = response.getItems();

            // 根区间是没有parentCompartmentId的区间
            for (Compartment compartment : compartments) {
                if (compartment.getCompartmentId().equals(tenantId) && compartment.getId().equals(compartment.getCompartmentId())) {
                    return compartment.getId(); // 返回根区间ID
                }
            }
        } catch (Exception e) {
            return tenantId;
        }

        // 如果没有找到根区间，返回租户ID作为默认值
        return tenantId;
    }

    public String getPrivateIpIdForVnic(Vnic vnic) {
        ListPrivateIpsRequest listPrivateIpsRequest = ListPrivateIpsRequest.builder()
                .vnicId(vnic.getId())
                .build();

        ListPrivateIpsResponse privateIpsResponse = virtualNetworkClient.listPrivateIps(listPrivateIpsRequest);

        for (PrivateIp privateIp : privateIpsResponse.getItems()) {
            // 返回第一个 Private IP 的 ID
            return privateIp.getId();
        }
        throw new RuntimeException("No Private IP found for VNIC ID: " + vnic.getId());
    }

    public void releaseUnusedPublicIps() {
        ListPublicIpsRequest listPublicIpsRequest = ListPublicIpsRequest.builder()
                .compartmentId(compartmentId)
                .scope(ListPublicIpsRequest.Scope.Region)
                .build();

        ListPublicIpsResponse response = virtualNetworkClient.listPublicIps(listPublicIpsRequest);
        List<PublicIp> publicIpList = response.getItems();
        if (publicIpList.isEmpty()) {
            return;
        }
        for (PublicIp publicIp : response.getItems()) {
            if (publicIp.getAssignedEntityId() == null) {  // 检查是否未分配到实例
                DeletePublicIpRequest deleteRequest = DeletePublicIpRequest.builder()
                        .publicIpId(publicIp.getId())
                        .build();
                virtualNetworkClient.deletePublicIp(deleteRequest);
                log.info("Released unused Public IP: {}", publicIp.getIpAddress());
            }
        }
    }

    public String reassignEphemeralPublicIp(Vnic vnic) {
        if (vnic == null) {
            throw new RuntimeException("当前实例的VNIC不存在");
        }
        String vnicId = vnic.getId();
        if (vnicId != null) {
            // Step 1: 解除当前的 Public IP（如果已存在）
            GetVnicRequest getVnicRequest = GetVnicRequest.builder().vnicId(vnicId).build();
            String existingPublicIpAddress = virtualNetworkClient.getVnic(getVnicRequest).getVnic().getPublicIp();
            if (StrUtil.isNotBlank(existingPublicIpAddress)) {
                // Step 1: 查找公网 IP 的 OCID
                GetPublicIpByIpAddressRequest getPublicIpByIpAddressRequest = GetPublicIpByIpAddressRequest.builder()
                        .getPublicIpByIpAddressDetails(
                                GetPublicIpByIpAddressDetails.builder()
                                        .ipAddress(existingPublicIpAddress)
                                        .build())
                        .build();

                String existingPublicIpId = virtualNetworkClient.getPublicIpByIpAddress(getPublicIpByIpAddressRequest).getPublicIp().getId();

                DeletePublicIpRequest deleteRequest = DeletePublicIpRequest.builder()
                        .publicIpId(existingPublicIpId)
                        .build();
                virtualNetworkClient.deletePublicIp(deleteRequest);
//                System.out.println("Existing public IP detached: " + existingPublicIpId);
            }
        }

        String publicIp;
        try {
            String privateIpId = getPrivateIpIdForVnic(vnic);
            // Step 1: 创建一个 Reserved Public IP
            CreatePublicIpDetails createPublicIpDetails = CreatePublicIpDetails.builder()
                    .compartmentId(compartmentId)
                    .lifetime(Ephemeral)  // 设置为 Reserved
                    .displayName("publicIp")
                    .privateIpId(privateIpId)
                    .build();
            CreatePublicIpRequest createRequest = CreatePublicIpRequest.builder()
                    .createPublicIpDetails(createPublicIpDetails)
                    .build();

            PublicIp reservedPublicIp = virtualNetworkClient.createPublicIp(createRequest).getPublicIp();
//            log.info("Reserved Public IP created: {}", reservedPublicIp.getIpAddress());

            // Step 2: 使用 UpdatePublicIpRequest 将 Reserved Public IP 关联到 VNIC
            UpdatePublicIpDetails updatePublicIpDetails = UpdatePublicIpDetails.builder()
                    .privateIpId(privateIpId)
                    .build();
            UpdatePublicIpRequest updateRequest = UpdatePublicIpRequest.builder()
                    .publicIpId(reservedPublicIp.getId())
                    .updatePublicIpDetails(updatePublicIpDetails)
                    .build();

            virtualNetworkClient.updatePublicIp(updateRequest);
//            log.info("Reserved Public IP attached to VNIC: " + reservedPublicIp.getIpAddress());
            publicIp = reservedPublicIp.getIpAddress();
            return publicIp;
        } catch (Exception e) {
            log.error("【更换公共IP】用户：[{}] ，区域：[{}] 更换IP任务异常，稍后将重试......", user.getUsername(), user.getOciCfg().getRegion());
        } finally {
            releaseUnusedPublicIps();
        }
        return null;
    }

    public BootVolume getBootVolumeByInstanceId(String instanceId) {
        BootVolume bootVolume = null;
        List<AvailabilityDomain> availabilityDomains = getAvailabilityDomains(identityClient, compartmentId);
        for (AvailabilityDomain availabilityDomain : availabilityDomains) {
            List<String> BootVolumeIdList = computeClient.listBootVolumeAttachments(ListBootVolumeAttachmentsRequest.builder()
                            .availabilityDomain(availabilityDomain.getName())
                            .compartmentId(compartmentId)
                            .instanceId(instanceId)
                            .build()).getItems()
                    .stream().map(BootVolumeAttachment::getBootVolumeId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (CollectionUtil.isNotEmpty(BootVolumeIdList)) {
                GetBootVolumeRequest getBootVolumeRequest = GetBootVolumeRequest.builder()
                        .bootVolumeId(BootVolumeIdList.get(0))
                        .build();
                GetBootVolumeResponse getBootVolumeResponse = blockstorageClient.getBootVolume(getBootVolumeRequest);
                bootVolume = getBootVolumeResponse.getBootVolume();
            }
        }
        if (null == bootVolume) {
            throw new OciException(-1, "引导卷不存在");
        }
        return bootVolume;
    }

    public List<BootVolume> listBootVolumeListByInstanceId(String instanceId) {
        List<BootVolume> bootVolumes = new ArrayList<>();
        List<AvailabilityDomain> availabilityDomains = getAvailabilityDomains(identityClient, compartmentId);
        for (AvailabilityDomain availabilityDomain : availabilityDomains) {
            List<String> BootVolumeIdList = computeClient.listBootVolumeAttachments(ListBootVolumeAttachmentsRequest.builder()
                            .availabilityDomain(availabilityDomain.getName())
                            .compartmentId(compartmentId)
                            .instanceId(instanceId)
                            .build()).getItems()
                    .stream().map(BootVolumeAttachment::getBootVolumeId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (CollectionUtil.isNotEmpty(BootVolumeIdList)) {
                bootVolumes.addAll(BootVolumeIdList.parallelStream()
                        .map(this::getBootVolumeById)
                        .collect(Collectors.toList()));
            }
        }
        if (bootVolumes.isEmpty()) {
            throw new OciException(-1, "实例引导卷不存在");
        }
        return bootVolumes;
    }

    public BootVolume getBootVolumeById(String bootVolumeId) {
        GetBootVolumeRequest getBootVolumeRequest = GetBootVolumeRequest.builder()
                .bootVolumeId(bootVolumeId)
                .build();
        GetBootVolumeResponse getBootVolumeResponse = blockstorageClient.getBootVolume(getBootVolumeRequest);
        return getBootVolumeResponse.getBootVolume();
    }

    public List<BootVolume> listBootVolume() {
        List<BootVolume> bootVolumes = new ArrayList<>();
        List<AvailabilityDomain> availabilityDomains = getAvailabilityDomains(identityClient, compartmentId);
        availabilityDomains.parallelStream().forEach(availabilityDomain -> {
            List<BootVolume> items = blockstorageClient.listBootVolumes(ListBootVolumesRequest.builder()
                    .availabilityDomain(availabilityDomain.getName())
                    .compartmentId(compartmentId)
                    .build()).getItems();
            if (null != items && !items.isEmpty()) {
                bootVolumes.addAll(items.stream().map(BootVolume::getId)
                        .collect(Collectors.toList())
                        .parallelStream().map(this::getBootVolumeById)
                        .collect(Collectors.toList()));
            }
        });

        return bootVolumes;
    }

    public void terminateBootVolume(String bootVolumeId) {
        blockstorageClient.deleteBootVolume(DeleteBootVolumeRequest.builder()
                .bootVolumeId(bootVolumeId)
                .build());
    }

    public OciCfgDetailsRsp.InstanceInfo getInstanceInfo(String instanceId) {
        Instance instance = getInstanceById(instanceId);
        String bootVolumeSize = null;
        try {
            BootVolume bootVolume = getBootVolumeByInstanceId(instanceId);
            bootVolumeSize = bootVolume.getSizeInGBs() + "";
        } catch (Exception e) {
            log.error("用户：[{}] ，区域：[{}] ， 实例：[{}] ，连接超时或者实例的引导卷不存在~",
                    user.getUsername(), user.getOciCfg().getRegion(),
                    instance.getDisplayName(), e);
        }

        // 打印引导卷大小（以 GB 为单位）
        return OciCfgDetailsRsp.InstanceInfo.builder()
                .ocId(instanceId)
                .region(instance.getRegion())
                .name(instance.getDisplayName())
                .shape(instance.getShape())
                .publicIp(listInstanceIPs(instanceId).stream()
                        .map(Vnic::getPublicIp)
                        .collect(Collectors.toList()))
                .enableChangeIp(TASK_MAP.get(CommonUtils.CREATE_TASK_PREFIX + instanceId) != null ? 1 : 0)
                .ocpus(String.valueOf(instance.getShapeConfig().getOcpus()))
                .memory(String.valueOf(instance.getShapeConfig().getMemoryInGBs()))
                .bootVolumeSize(bootVolumeSize)
                .createTime(CommonUtils.dateFmt2String(instance.getTimeCreated()))
                .state(instance.getLifecycleState().getValue())
                .availabilityDomain(instance.getAvailabilityDomain())
                .vnicList(listVnicByInstanceId(instanceId).stream()
                        .map(x -> new OciCfgDetailsRsp.InstanceVnicInfo(x.getId(), x.getDisplayName() + "（" + x.getPublicIp() + "）"))
                        .collect(Collectors.toList()))
                .build();
    }

    public String updateInstanceState(String instanceId, InstanceActionEnum action) {
        if (action == null) {
            throw new OciException(-1, "实例操作不存在");
        }

        InstanceActionRequest request = InstanceActionRequest.builder()
                .instanceId(instanceId)
                .action(action.getAction()) // "START" or "STOP"
                .build();

        InstanceActionResponse response = computeClient.instanceAction(request);
        String currentState = response.getInstance().getLifecycleState().getValue();
        log.info("用户：[{}] ，区域：[{}] 修改实例：[{}] 状态成功！实例当前状态: [{}]",
                user.getUsername(), user.getOciCfg().getRegion(),
                response.getInstance().getDisplayName(), currentState);
        return currentState;
    }

    public void terminateInstance(String instanceId, boolean preserveBootVolume, boolean preserveDataVolumesCreatedAtLaunch) {
        TerminateInstanceRequest terminateInstanceRequest = TerminateInstanceRequest.builder()
                .instanceId(instanceId)
//                .ifMatch("EXAMPLE-ifMatch-Value")
                .preserveBootVolume(preserveBootVolume) // 是否删除或保留引导卷 ，默认false不保留
                .preserveDataVolumesCreatedAtLaunch(preserveDataVolumesCreatedAtLaunch) // 是否删除或保留启动期间创建的数据卷，默认true保留
                .build();

        /* Send request to the Client */
        TerminateInstanceResponse response = computeClient.terminateInstance(terminateInstanceRequest);
    }

    public List<Vnic> listVnicByInstanceId(String instanceId) {
        List<Vnic> vnics = listInstanceIPs(instanceId);
        if (vnics.isEmpty()) {
            return null;
        }
        return vnics;
    }

    public Vnic getVnicByInstanceId(String instanceId) {
        List<Vnic> vnics = listInstanceIPs(instanceId);
        if (vnics.isEmpty()) {
            return null;
        }
        for (Vnic vnic : vnics) {
            if (vnic.getIsPrimary()) {
                return vnic;
            }
        }
        return vnics.get(0);
    }

    public Vcn getVcnById(String vcnId) {
        if (vcnId == null) {
            return null;
        }
        GetVcnResponse getVcnResponse = virtualNetworkClient.getVcn(GetVcnRequest.builder().vcnId(vcnId).build());
        return getVcnResponse.getVcn();
    }

    public InstanceCfgDTO getInstanceCfg(String instanceId) {
        Instance instance = getInstanceById(instanceId);
        List<String> ipv6Addresses = getVnicByInstanceId(instanceId).getIpv6Addresses();
        String ipv6 = (null == ipv6Addresses || ipv6Addresses.isEmpty()) ? null : ipv6Addresses.get(0);

        String bootVolumeSize = null;
        String bootVolumeVpu = null;
        try {
            BootVolume bootVolume = getBootVolumeByInstanceId(instanceId);
            bootVolumeSize = bootVolume.getSizeInGBs() + "";
            bootVolumeVpu = bootVolume.getVpusPerGB() + "";
        } catch (Exception e) {
            log.error("用户：[{}] ，区域：[{}] ， 实例：[{}] 的引导卷不存在~",
                    user.getUsername(), user.getOciCfg().getRegion(),
                    instance.getDisplayName());
        }

        return InstanceCfgDTO.builder()
                .instanceName(instance.getDisplayName())
                .ipv6(ipv6)
                .ocpus(String.valueOf(instance.getShapeConfig().getOcpus()))
                .memory(String.valueOf(instance.getShapeConfig().getMemoryInGBs()))
                .bootVolumeSize(bootVolumeSize)
                .bootVolumeVpu(bootVolumeVpu)
                .build();
    }

    private void updateRouteRules(InternetGateway gateway, Vcn vcn) {
        RouteRule v4Route = RouteRule.builder()
//                .cidrBlock("0.0.0.0/0")
                .destination("0.0.0.0/0")
                .destinationType(RouteRule.DestinationType.CidrBlock)
                .routeType(RouteRule.RouteType.Static)
                .networkEntityId(gateway.getId())
                .build();
        RouteRule v6Route = RouteRule.builder()
//                .cidrBlock("::/0")
                .destination("::/0")
                .destinationType(RouteRule.DestinationType.CidrBlock)
                .routeType(RouteRule.RouteType.Static)
                .networkEntityId(gateway.getId())
                .build();
        List<RouteRule> routeRules = Arrays.asList(v4Route, v6Route);
//        GetRouteTableRequest getRouteTableRequest = GetRouteTableRequest.builder().rtId(vcn.getDefaultRouteTableId()).build();
//        GetRouteTableResponse getRouteTableResponse = virtualNetworkClient.getRouteTable(getRouteTableRequest);
//        RouteTable routeTable = getRouteTableResponse.getRouteTable();
//        if (routeTable.getRouteRules().isEmpty()) {
//            routeRules.add(v4Route);
//            routeRules.add(v6Route);
//        } else {
//            for (RouteRule rule : routeTable.getRouteRules()) {
//                if (!rule.getDestination().contains(v4Route.getDestination())) {
//                    routeRules.add(v4Route);
//                }
//                if (!rule.getDestination().contains(v6Route.getDestination())) {
//                    routeRules.add(v6Route);
//                }
//            }
//        }

//        System.out.println(routeRules);
        UpdateRouteTableRequest updateRouteTableRequest = UpdateRouteTableRequest.builder()
                .rtId(vcn.getDefaultRouteTableId())
                .updateRouteTableDetails(UpdateRouteTableDetails.builder()
                        .routeRules(routeRules)
                        .build())
                .build();
        virtualNetworkClient.updateRouteTable(updateRouteTableRequest);
        log.info("用户：[{}] ，区域：[{}] 更新了 VCN：{} 的路由表",
                user.getUsername(), user.getOciCfg().getRegion(), vcn.getDisplayName());
    }

    public void releaseSecurityRule(Vcn vcn, Integer type) {
        IngressSecurityRule in4 = IngressSecurityRule.builder()
                .sourceType(IngressSecurityRule.SourceType.CidrBlock)
                .source("0.0.0.0/0")
                .protocol("all")
                .build();
        EgressSecurityRule out4 = EgressSecurityRule.builder()
                .destinationType(EgressSecurityRule.DestinationType.CidrBlock)
                .destination("0.0.0.0/0")
                .protocol("all")
                .build();
        IngressSecurityRule in6 = IngressSecurityRule.builder()
                .sourceType(IngressSecurityRule.SourceType.CidrBlock)
                .source("::/0")
                .protocol("all")
                .build();
        EgressSecurityRule out6 = EgressSecurityRule.builder()
                .destinationType(EgressSecurityRule.DestinationType.CidrBlock)
                .destination("::/0")
                .protocol("all")
                .build();
        List<IngressSecurityRule> inList;
        List<EgressSecurityRule> outList;
        switch (type) {
            case 4:
                inList = Collections.singletonList(in4);
                outList = Collections.singletonList(out4);
                break;
            case 6:
                inList = Collections.singletonList(in6);
                outList = Collections.singletonList(out6);
                break;
            default:
                if (vcn.getIpv6CidrBlocks().isEmpty()) {
                    inList = Collections.singletonList(in4);
                    outList = Collections.singletonList(out4);
                } else {
                    inList = Arrays.asList(in4, in6);
                    outList = Arrays.asList(out4, out6);
                }
        }

        GetSecurityListRequest getSecurityListRequest = GetSecurityListRequest.builder().securityListId(vcn.getDefaultSecurityListId()).build();
        GetSecurityListResponse getSecurityListResponse = virtualNetworkClient.getSecurityList(getSecurityListRequest);
        List<IngressSecurityRule> ingressSecurityRules = getSecurityListResponse.getSecurityList().getIngressSecurityRules();
        if (ingressSecurityRules.isEmpty()) {
            ingressSecurityRules = inList;
        } else {
//            for (IngressSecurityRule rule : ingressSecurityRules) {
//                for (IngressSecurityRule in : inList) {
//                    if (!rule.getSource().equals(in.getSource()) &&
//                            !rule.getProtocol().equals(in.getProtocol()) &&
//                            !rule.getSourceType().equals(in.getSourceType())) {
//                        ingressSecurityRules.add(in);
//                    }
//                }
//            }
            ingressSecurityRules.addAll(inList);
        }
        List<EgressSecurityRule> egressSecurityRules = getSecurityListResponse.getSecurityList().getEgressSecurityRules();
        if (egressSecurityRules.isEmpty()) {
            egressSecurityRules = outList;
        } else {
//            for (EgressSecurityRule rule : egressSecurityRules) {
//                for (EgressSecurityRule out : outList) {
//                    if (!rule.getDestination().equals(out.getDestination()) &&
//                            !rule.getProtocol().equals(out.getProtocol()) &&
//                            !rule.getDestinationType().equals(out.getDestinationType())) {
//                        egressSecurityRules.add(out);
//                    }
//                }
//            }
            egressSecurityRules.addAll(outList);
        }
        virtualNetworkClient.updateSecurityList(UpdateSecurityListRequest.builder()
                .securityListId(vcn.getDefaultSecurityListId())
                .updateSecurityListDetails(UpdateSecurityListDetails.builder()
                        .ingressSecurityRules(ingressSecurityRules)
                        .egressSecurityRules(egressSecurityRules)
                        .build())
                .build());
        log.info("用户：[{}] ，区域：[{}] 放行了 VCN：{} 的安全列表中 {} 的所有端口及协议",
                user.getUsername(), user.getOciCfg().getRegion(),
                vcn.getDisplayName(), type == 0 ? "所有地址" : "IPV" + type);
    }

    public Ipv6 createIpv6(Vnic vnic, Vcn vcn) {
        if (vcn == null) {
            try {
                vcn = createVcn(virtualNetworkClient, compartmentId, CIDR_BLOCK);
                createInternetGateway(virtualNetworkClient, compartmentId, vcn);
            } catch (Exception e) {
                log.error("用户：[{}] ，区域：[{}] 创建VCN失败", user.getUsername(), user.getOciCfg().getRegion());
                throw new OciException(-1, "创建VCN失败");
            }
        }

        String vcnId = vcn.getId();

        // 添加ipv6 cidr 前缀
        List<String> oldIpv6CidrBlocks = vcn.getIpv6CidrBlocks();
        if (null == oldIpv6CidrBlocks || oldIpv6CidrBlocks.isEmpty()) {
            try {
                virtualNetworkClient.addIpv6VcnCidr(AddIpv6VcnCidrRequest.builder()
                        .vcnId(vcnId)
                        .addVcnIpv6CidrDetails(AddVcnIpv6CidrDetails.builder()
                                .isOracleGuaAllocationEnabled(true)
                                .build())
                        .build());
            } catch (Exception e) {
                log.error("添加ipv6 cidr 前缀失败", e);
                throw new OciException(-1, "添加ipv6 cidr 前缀失败");
            } finally {
                vcn = getVcnById(vcn.getId());
                oldIpv6CidrBlocks = vcn.getIpv6CidrBlocks();
            }
        }

        // 子网
        List<Subnet> oldSubnet = listSubnets(vcnId);
        if (oldSubnet.isEmpty()) {
            try {
                log.warn("用户：[{}] ，区域：[{}] 正在创建子网~", user.getUsername(), user.getOciCfg().getRegion());
                oldSubnet.add(createSubnet(virtualNetworkClient,
                        compartmentId,
                        getAvailabilityDomains(identityClient, compartmentId).get(0),
                        CIDR_BLOCK, vcn));
            } catch (Exception e) {
                log.error("用户：[{}] ，区域：[{}] 创建子网失败，原因：[{}]", user.getUsername(), user.getOciCfg().getRegion(), e.getLocalizedMessage());
                throw new OciException(-1, "创建子网失败");
            }
        }

        for (Subnet subnet : oldSubnet) {
            if (subnet.getVcnId().equals(vcnId)) {
                String v6Cidr = oldIpv6CidrBlocks.get(0);
                String subnetV6Cidr = v6Cidr.replaceAll("/56", "/64");
                if (null == subnet.getIpv6CidrBlock()) {
                    try {
                        virtualNetworkClient.updateSubnet(UpdateSubnetRequest.builder()
                                .subnetId(subnet.getId())
                                .updateSubnetDetails(UpdateSubnetDetails.builder()
                                        .ipv6CidrBlock(subnetV6Cidr)
                                        .build())
                                .build());
                    } catch (Exception e) {
                        if (e.getMessage().contains("has ULA CIDR(s) assigned")) {
                            log.warn("subnet ipv6CidrBlock: {} exists", subnetV6Cidr);
                        } else {
                            log.error("添加子网ipv6前缀失败", e);
                            throw new OciException(-1, "添加子网ipv6前缀失败");
                        }
                    }
                }
            }
        }

        // 更新路由表（默认存在）
        ListInternetGatewaysResponse listInternetGatewaysResponse = virtualNetworkClient.listInternetGateways(
                ListInternetGatewaysRequest.builder()
                        .compartmentId(compartmentId)
                        .vcnId(vcnId)
                        .build());
        if (listInternetGatewaysResponse.getItems().isEmpty()) {
            try {
                InternetGateway gateway = createInternetGateway(virtualNetworkClient, compartmentId, vcn);
                updateRouteRules(gateway, vcn);
            } catch (Exception e) {
                log.error("用户：[{}] ，区域：[{}] 创建网关失败，原因：[{}]", user.getUsername(), user.getOciCfg().getRegion(), e.getLocalizedMessage());
                throw new OciException(-1, "创建网关失败");
            }
        } else {
            for (InternetGateway gateway : listInternetGatewaysResponse.getItems()) {
                updateRouteRules(gateway, vcn);
            }
        }

        try {
            // 安全列表（默认存在）
            releaseSecurityRule(vcn, 6);
        } catch (Exception e) {
            log.error("release security rule error >>>>>>>>>>>>>>>>>> ", e);
        }

        CreateIpv6Response createIpv6Response = virtualNetworkClient.createIpv6(CreateIpv6Request.builder()
                .createIpv6Details(CreateIpv6Details.builder()
                        .vnicId(vnic.getId())
                        .build())
                .build());

        return createIpv6Response.getIpv6();
    }

    public void updateInstanceName(String instanceId, String name) {
        computeClient.updateInstance(UpdateInstanceRequest.builder()
                .instanceId(instanceId)
                .updateInstanceDetails(UpdateInstanceDetails.builder()
                        .displayName(name)
                        .build())
                .build());

        List<BootVolume> bootVolumes = listBootVolumeListByInstanceId(instanceId);
        bootVolumes.parallelStream().forEach(bootVolume -> {
            blockstorageClient.updateBootVolume(UpdateBootVolumeRequest.builder()
                    .bootVolumeId(bootVolume.getId())
                    .updateBootVolumeDetails(UpdateBootVolumeDetails.builder()
                            .displayName(name + " (Boot Volume)")
                            .build())
                    .build());
        });
    }

    public void updateInstanceCfg(String instanceId, float ocpus, float memory) {
        computeClient.updateInstance(UpdateInstanceRequest.builder()
                .instanceId(instanceId)
                .updateInstanceDetails(UpdateInstanceDetails.builder()
                        .shapeConfig(UpdateInstanceShapeConfigDetails.builder()
                                .ocpus(ocpus)
                                .memoryInGBs(memory)
                                .build())
                        .build())
                .build());
    }

    public void updateBootVolumeCfg(String bootVolumeId, long size, long vpusPer) {
        blockstorageClient.updateBootVolume(UpdateBootVolumeRequest.builder()
                .bootVolumeId(bootVolumeId)
                .updateBootVolumeDetails(UpdateBootVolumeDetails.builder()
                        .sizeInGBs(size)
                        .vpusPerGB(vpusPer)
                        .build())
                .build());
    }

    public SecurityList listSecurityRule(Vcn vcn) {
        GetSecurityListResponse getSecurityListResponse = virtualNetworkClient.getSecurityList(GetSecurityListRequest.builder()
                .securityListId(vcn.getDefaultSecurityListId())
                .build());
        return getSecurityListResponse.getSecurityList();
    }

    public void updateSecurityRuleList(Vcn vcn, UpdateSecurityRuleListParams params) {
        SecurityList securityList = listSecurityRule(vcn);
        List<IngressSecurityRule> ingressSecurityRuleList = params.getIngressRuleList().parallelStream()
                .map(ingressRule -> {
                    IngressSecurityRule.Builder builder = IngressSecurityRule.builder()
                            .isStateless(ingressRule.getIsStateless())
                            .protocol(ingressRule.getProtocol())
                            .source(ingressRule.getSource())
                            .sourceType(IngressSecurityRule.SourceType.create(ingressRule.getSourceType()))
                            .description(ingressRule.getDescription());

                    if (ingressRule.getIcmpOptions().getType() != null) {
                        builder.icmpOptions(ingressRule.getIcmpOptions());
                    }

                    if ("6".equals(ingressRule.getProtocol())) {
                        TcpOptions.Builder tcpBuilder = TcpOptions.builder();
                        if (ingressRule.getTcpSourcePortMin() == null && ingressRule.getTcpSourcePortMax() == null &&
                                ingressRule.getTcpDesPortMin() != null && ingressRule.getTcpDesPortMax() != null) {
                            tcpBuilder.destinationPortRange(PortRange.builder()
                                    .min(ingressRule.getTcpDesPortMin())
                                    .max(ingressRule.getTcpDesPortMax())
                                    .build());
                        } else if (ingressRule.getTcpSourcePortMin() != null && ingressRule.getTcpSourcePortMax() != null &&
                                ingressRule.getTcpDesPortMin() == null && ingressRule.getTcpDesPortMax() == null) {
                            tcpBuilder.sourcePortRange(PortRange.builder()
                                    .min(ingressRule.getTcpSourcePortMin())
                                    .max(ingressRule.getTcpSourcePortMax())
                                    .build());
                        } else {
                            tcpBuilder.sourcePortRange(PortRange.builder()
                                            .min(ingressRule.getTcpSourcePortMin())
                                            .max(ingressRule.getTcpSourcePortMax())
                                            .build())
                                    .destinationPortRange(PortRange.builder()
                                            .min(ingressRule.getTcpDesPortMin())
                                            .max(ingressRule.getTcpDesPortMax())
                                            .build());
                        }
                        builder.tcpOptions(tcpBuilder.build());
                    }

                    if ("17".equals(ingressRule.getProtocol())) {
                        UdpOptions.Builder udpBuilder = UdpOptions.builder();
                        if (ingressRule.getUdpSourcePortMin() == null && ingressRule.getUdpSourcePortMax() == null &&
                                ingressRule.getUdpDesPortMin() != null && ingressRule.getUdpDesPortMax() != null) {
                            udpBuilder.destinationPortRange(PortRange.builder()
                                    .min(ingressRule.getUdpDesPortMin())
                                    .max(ingressRule.getUdpDesPortMax())
                                    .build());
                        } else if (ingressRule.getUdpSourcePortMin() != null && ingressRule.getUdpSourcePortMax() != null &&
                                ingressRule.getUdpDesPortMin() == null && ingressRule.getUdpDesPortMax() == null) {
                            udpBuilder.sourcePortRange(PortRange.builder()
                                    .min(ingressRule.getUdpSourcePortMin())
                                    .max(ingressRule.getUdpSourcePortMax())
                                    .build());
                        } else {
                            udpBuilder.sourcePortRange(PortRange.builder()
                                            .min(ingressRule.getUdpSourcePortMin())
                                            .max(ingressRule.getUdpSourcePortMax())
                                            .build())
                                    .destinationPortRange(PortRange.builder()
                                            .min(ingressRule.getUdpDesPortMin())
                                            .max(ingressRule.getUdpDesPortMax())
                                            .build());
                        }
                        builder.udpOptions(udpBuilder.build());
                    }

                    return builder.build();
                })
                .collect(Collectors.toList());
        ingressSecurityRuleList.addAll(securityList.getIngressSecurityRules());

        List<EgressSecurityRule> egressSecurityRuleList = params.getEgressRuleList().parallelStream()
                .map(egressRule -> {
                    EgressSecurityRule.Builder builder = EgressSecurityRule.builder()
                            .destination(egressRule.getDestination())
                            .destinationType(EgressSecurityRule.DestinationType.create(egressRule.getDestinationType()))
                            .isStateless(egressRule.getIsStateless())
                            .protocol(egressRule.getProtocol())
                            .description(egressRule.getDescription());

                    if (egressRule.getIcmpOptions().getType() != null) {
                        builder.icmpOptions(egressRule.getIcmpOptions());
                    }

                    if ("6".equals(egressRule.getProtocol())) {
                        TcpOptions.Builder tcpBuilder = TcpOptions.builder();
                        if (egressRule.getTcpSourcePortMin() == null && egressRule.getTcpSourcePortMax() == null &&
                                egressRule.getTcpDesPortMin() != null && egressRule.getTcpDesPortMax() != null) {
                            tcpBuilder.destinationPortRange(PortRange.builder()
                                    .min(egressRule.getTcpDesPortMin())
                                    .max(egressRule.getTcpDesPortMax())
                                    .build());
                        } else if (egressRule.getTcpSourcePortMin() != null && egressRule.getTcpSourcePortMax() != null &&
                                egressRule.getTcpDesPortMin() == null && egressRule.getTcpDesPortMax() == null) {
                            tcpBuilder.sourcePortRange(PortRange.builder()
                                    .min(egressRule.getTcpSourcePortMin())
                                    .max(egressRule.getTcpSourcePortMax())
                                    .build());
                        } else {
                            tcpBuilder.sourcePortRange(PortRange.builder()
                                            .min(egressRule.getTcpSourcePortMin())
                                            .max(egressRule.getTcpSourcePortMax())
                                            .build())
                                    .destinationPortRange(PortRange.builder()
                                            .min(egressRule.getTcpDesPortMin())
                                            .max(egressRule.getTcpDesPortMax())
                                            .build());
                        }
                        builder.tcpOptions(tcpBuilder.build());
                    }

                    if ("17".equals(egressRule.getProtocol())) {
                        UdpOptions.Builder udpBuilder = UdpOptions.builder();
                        if (egressRule.getUdpSourcePortMin() == null && egressRule.getUdpSourcePortMax() == null &&
                                egressRule.getUdpDesPortMin() != null && egressRule.getUdpDesPortMax() != null) {
                            udpBuilder.destinationPortRange(PortRange.builder()
                                    .min(egressRule.getUdpDesPortMin())
                                    .max(egressRule.getUdpDesPortMax())
                                    .build());
                        } else if (egressRule.getUdpSourcePortMin() != null && egressRule.getUdpSourcePortMax() != null &&
                                egressRule.getUdpDesPortMin() == null && egressRule.getUdpDesPortMax() == null) {
                            udpBuilder.sourcePortRange(PortRange.builder()
                                    .min(egressRule.getUdpSourcePortMin())
                                    .max(egressRule.getUdpSourcePortMax())
                                    .build());
                        } else {
                            udpBuilder.sourcePortRange(PortRange.builder()
                                            .min(egressRule.getUdpSourcePortMin())
                                            .max(egressRule.getUdpSourcePortMax())
                                            .build())
                                    .destinationPortRange(PortRange.builder()
                                            .min(egressRule.getUdpDesPortMin())
                                            .max(egressRule.getUdpDesPortMax())
                                            .build());
                        }
                        builder.udpOptions(udpBuilder.build());
                    }

                    return builder.build();
                })
                .collect(Collectors.toList());
        egressSecurityRuleList.addAll(securityList.getEgressSecurityRules());

        virtualNetworkClient.updateSecurityList(UpdateSecurityListRequest.builder()
                .securityListId(vcn.getDefaultSecurityListId())
                .updateSecurityListDetails(UpdateSecurityListDetails.builder()
                        .ingressSecurityRules(ingressSecurityRuleList)
                        .egressSecurityRules(egressSecurityRuleList)
                        .build())
                .build());
    }
}
