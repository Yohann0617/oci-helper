package com.yohann.ocihelper.config;

import cn.hutool.core.collection.CollectionUtil;
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
import com.oracle.bmc.identity.model.AvailabilityDomain;
import com.oracle.bmc.identity.model.Compartment;
import com.oracle.bmc.identity.requests.ListAvailabilityDomainsRequest;
import com.oracle.bmc.identity.requests.ListCompartmentsRequest;
import com.oracle.bmc.identity.responses.ListAvailabilityDomainsResponse;
import com.oracle.bmc.identity.responses.ListCompartmentsResponse;
import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.workrequests.WorkRequestClient;
import com.yohann.ocihelper.bean.dto.InstanceDetailDTO;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.response.OciCfgDetailsRsp;
import com.yohann.ocihelper.enums.*;
import com.yohann.ocihelper.exception.OciException;
import com.yohann.ocihelper.utils.CommonUtils;
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
public class OracleInstanceFetcher implements Closeable {

    private final SysUserDTO user;
    private final ComputeClient computeClient;
    private final IdentityClient identityClient;
    private final WorkRequestClient workRequestClient;
    private final ComputeWaiters computeWaiters;
    private final VirtualNetworkClient virtualNetworkClient;
    private final BlockstorageClient blockstorageClient;
    private final String compartmentId;

    private static final String CIDR_BLOCK = "10.0.0.0/16";
    private static final String IPV6_CIDR_BLOCK = "2603:c021:c009:4200::/56";
    private static final String SUBNET_V6_CIDR = "2603:c021:c005:fe7e::/64";

    @Override
    public void close() {
        computeClient.close();
        identityClient.close();
        workRequestClient.close();
        virtualNetworkClient.close();
        blockstorageClient.close();
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
        compartmentId = findRootCompartment(identityClient, provider.getTenantId());
        identityClient.setRegion(ociCfg.getRegion());

        computeClient = ComputeClient.builder().build(provider);
        computeClient.setRegion(ociCfg.getRegion());

        blockstorageClient = BlockstorageClient.builder().build(provider);
        blockstorageClient.setRegion(ociCfg.getRegion());

        workRequestClient = WorkRequestClient.builder().build(provider);
        workRequestClient.setRegion(ociCfg.getRegion());
        computeWaiters = computeClient.newWaiters(workRequestClient);

        virtualNetworkClient = VirtualNetworkClient.builder().build(provider);
        virtualNetworkClient.setRegion(ociCfg.getRegion());
    }

    public InstanceDetailDTO createInstanceData() {
        InstanceDetailDTO instanceDetailDTO = new InstanceDetailDTO();
        instanceDetailDTO.setTaskId(user.getTaskId());
        instanceDetailDTO.setUsername(user.getUsername());
        instanceDetailDTO.setRegion(user.getOciCfg().getRegion());
        instanceDetailDTO.setArchitecture(user.getArchitecture());
        instanceDetailDTO.setCreateNumbers(user.getCreateNumbers());

        List<AvailabilityDomain> availabilityDomains = getAvailabilityDomains(identityClient, compartmentId);
        List<Vcn> vcnList = listVcn();
        int size = availabilityDomains.size();
        Vcn vcn;
        InternetGateway internetGateway;
        Subnet subnet;
        NetworkSecurityGroup networkSecurityGroup;
        LaunchInstanceDetails launchInstanceDetails;
        Instance instance;
        try {
            for (AvailabilityDomain availableDomain : availabilityDomains) {
                try {
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
                            String networkCidrBlock = getCidr(virtualNetworkClient, compartmentId);
                            vcn = createVcn(virtualNetworkClient, compartmentId, networkCidrBlock);
                            internetGateway = createInternetGateway(virtualNetworkClient, compartmentId, vcn);
                            addInternetGatewayToDefaultRouteTable(virtualNetworkClient, vcn, internetGateway);
                            subnet = createSubnet(virtualNetworkClient, compartmentId, availableDomain, networkCidrBlock, vcn);
                            if (null == subnet) {
                                continue;
                            }
                            networkSecurityGroup = createNetworkSecurityGroup(virtualNetworkClient, compartmentId, vcn);
                            addNetworkSecurityGroupSecurityRules(virtualNetworkClient, networkSecurityGroup, networkCidrBlock);
                            log.info("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] 检测到VCN不存在，正在创建VCN......",
                                    user.getUsername(), user.getOciCfg().getRegion(), user.getArchitecture());
                        } else {
                            subnet = listSubnets(vcnList.get(0).getId()).get(0);
                            networkSecurityGroup = null;
                            log.info("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] ，检测到VCN：{} 存在，默认使用该VCN创建实例......",
                                    user.getUsername(), user.getOciCfg().getRegion(), user.getArchitecture(), vcnList.get(0).getDisplayName());
                        }

                        String cloudInitScript = CommonUtils.getPwdShell(user.getRootPassword());
                        launchInstanceDetails = createLaunchInstanceDetails(
                                compartmentId, availableDomain,
                                shape, image,
                                subnet, networkSecurityGroup,
                                cloudInitScript, user);
                        instance = createInstance(computeWaiters, launchInstanceDetails);
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
                        } else if (error.getStatusCode() == 400 || error.getMessage().contains(ErrorEnum.LIMIT_EXCEEDED.getErrorType())) {
                            instanceDetailDTO.setOut(true);
                            log.error("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] 无法创建实例，配额已经超过限制~",
                                    user.getUsername(), user.getOciCfg().getRegion(), user.getArchitecture());
                            return instanceDetailDTO;
                        } else if (error.getStatusCode() == 429 || error.getMessage().contains(ErrorEnum.TOO_MANY_REQUESTS.getErrorType())) {
                            log.warn("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] 开机请求频繁，[{}]秒后将重试......",
                                    user.getUsername(), user.getOciCfg().getRegion(), user.getArchitecture(), user.getInterval());
                        } else {
//                            instanceDetailDTO.setOut(true);
                            log.warn("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] 出现错误了，原因为：{}",
                                    user.getUsername(), user.getOciCfg().getRegion(), user.getArchitecture(),
                                    e.getMessage(), e);
                            return instanceDetailDTO;
                        }
                    } else {
//                        instanceDetailDTO.setOut(true);
                        log.warn("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] 出现错误了，原因为：{}",
                                user.getUsername(), user.getOciCfg().getRegion(), user.getArchitecture(),
                                e.getMessage(), e);
                        return instanceDetailDTO;
                    }
                }
            }
        } catch (Exception e) {
//            instanceDetailDTO.setOut(true);
            log.warn("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] 出现错误了，原因为：{}",
                    user.getUsername(), user.getOciCfg().getRegion(), user.getArchitecture(),
                    e.getMessage(), e);
            return instanceDetailDTO;
        }
        return instanceDetailDTO;
    }

    public SysUserDTO getUser() {
        return user;
    }

    public List<Instance> listInstances() {
        ListInstancesRequest request = ListInstancesRequest.builder()
                .compartmentId(user.getOciCfg().getTenantId())
                .build();
        ListInstancesResponse response = computeClient.listInstances(request);

        return response.getItems().parallelStream()
                .filter(x -> x.getLifecycleState().getValue().equals(InstanceStateEnum.LIFECYCLE_STATE_RUNNING.getState()))
                .collect(Collectors.toList());
    }

    public Instance getInstanceById(String instanceId) {
        List<Instance> instances = listInstances().stream()
                .filter(x -> x.getId().equals(instanceId))
                .collect(Collectors.toList());
        return instances.size() == 1 ? instances.get(0) : null;
    }

    public List<Vnic> listInstanceIPs(String instanceId) {
        // 获取实例的所有VNIC附件
        ListVnicAttachmentsRequest vnicRequest = ListVnicAttachmentsRequest.builder()
                .compartmentId(compartmentId)
                .instanceId(instanceId)
                .build();

        ListVnicAttachmentsResponse vnicResponse = computeClient.listVnicAttachments(vnicRequest);
        List<VnicAttachment> vnicAttachments = vnicResponse.getItems();

        return vnicAttachments.parallelStream().map(x -> {
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

    private List<AvailabilityDomain> getAvailabilityDomains(
            IdentityClient identityClient, String compartmentId) {
        ListAvailabilityDomainsResponse listAvailabilityDomainsResponse =
                identityClient.listAvailabilityDomains(ListAvailabilityDomainsRequest.builder()
                        .compartmentId(compartmentId)
                        .build());
        return listAvailabilityDomainsResponse.getItems();
    }

    private List<Shape> getShape(
            ComputeClient computeClient,
            String compartmentId,
            AvailabilityDomain availabilityDomain,
            SysUserDTO user) {
        ListShapesRequest listShapesRequest =
                ListShapesRequest.builder()
                        .availabilityDomain(availabilityDomain.getName())
                        .compartmentId(compartmentId)
                        .build();
        ListShapesResponse listShapesResponse = computeClient.listShapes(listShapesRequest);
        List<Shape> shapes = listShapesResponse.getItems();
//        if (shapes.isEmpty()) {
////            throw new IllegalStateException("No available shape was found.");
//            log.warn("AvailabilityDomain: {} No available shape was found.", availabilityDomain.getName());
//        }
        List<Shape> vmShapes = shapes.isEmpty() ? Collections.emptyList() : shapes.stream()
                .filter(shape -> shape.getShape().startsWith("VM"))
                .collect(Collectors.toList());
//        if (vmShapes.isEmpty()) {
////            throw new IllegalStateException("No available VM shape was found.");
//            log.warn("AvailabilityDomain: {} No available VM shape was found.", availabilityDomain.getName());
//        }
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

    private Vcn createVcn(VirtualNetworkClient virtualNetworkClient, String compartmentId, String cidrBlock)
            throws Exception {
        String vcnName = "oci-helper-vcn";
        ListVcnsRequest build = ListVcnsRequest.builder().compartmentId(compartmentId)
                .displayName(vcnName)
                .build();

        ListVcnsResponse listVcnsResponse = virtualNetworkClient.listVcns(build);
        if (listVcnsResponse.getItems().size() > 0) {
            return listVcnsResponse.getItems().get(0);
        }
        CreateVcnDetails createVcnDetails = CreateVcnDetails.builder()
                .cidrBlock(cidrBlock)
                .isIpv6Enabled(true)
//                .isOracleGuaAllocationEnabled(true)
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
        ListVcnsRequest build = ListVcnsRequest.builder().compartmentId(compartmentId)
                .displayName(vcnName)
                .build();

        ListVcnsResponse listVcnsResponse = virtualNetworkClient.listVcns(build);
        if (listVcnsResponse.getItems().size() > 0) {
            return listVcnsResponse.getItems().get(0);
        }
        CreateVcnDetails createVcnDetails = CreateVcnDetails.builder()
                .cidrBlock(cidrBlock)
                .isIpv6Enabled(true)
//                .isOracleGuaAllocationEnabled(true)
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
                .build();
        ListVcnsResponse response = virtualNetworkClient.listVcns(request);
        return response.getItems();
    }

    private InternetGateway createInternetGateway(
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

    private void addInternetGatewayToDefaultRouteTable(
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

    private Subnet createSubnet(
            VirtualNetworkClient virtualNetworkClient,
            String compartmentId,
            AvailabilityDomain availabilityDomain,
            String networkCidrBlock,
            Vcn vcn)
            throws Exception {
        String subnetName = "oci-helper-subnet";
        Subnet subnet = null;
        //检查子网是否存在
        ListSubnetsRequest listRequest = ListSubnetsRequest.builder()
                .compartmentId(compartmentId)
                .vcnId(vcn.getId())
                .displayName(subnetName)
                .build();
        ListSubnetsResponse listResponse = virtualNetworkClient.listSubnets(listRequest);
        if (listResponse.getItems().size() > 0) {
            // 如果找到已有的子网，返回其 ID
            List<Subnet> items = listResponse.getItems();
            int size = 0;
            for (Subnet subnetOld : listResponse.getItems()) {
                if (subnetOld.getAvailabilityDomain().equals(availabilityDomain.getName())) {
                    return subnetOld;
                } else {
                    //不匹配
                    size++;
                }
            }
            if (items.size() == size) {
                return null;
            }
        } else {

            CreateSubnetDetails createSubnetDetails =
                    CreateSubnetDetails.builder()
                            .availabilityDomain(availabilityDomain.getName())
                            .compartmentId(compartmentId)
                            .displayName(subnetName)
                            .cidrBlock(networkCidrBlock)
                            .ipv6CidrBlocks(Collections.singletonList(SUBNET_V6_CIDR))
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

            log.info("Created Subnet: " + subnet.getId());
            log.info("subnet: [{}]", subnet);

        }
        return subnet;
    }

    private void deleteSubnet(VirtualNetworkClient virtualNetworkClient, Subnet subnet) {
        try {
            DeleteSubnetRequest deleteSubnetRequest =
                    DeleteSubnetRequest.builder().subnetId(subnet.getId()).build();
            virtualNetworkClient.deleteSubnet(deleteSubnetRequest);

            GetSubnetRequest getSubnetRequest =
                    GetSubnetRequest.builder().subnetId(subnet.getId()).build();
            virtualNetworkClient
                    .getWaiters()
                    .forSubnet(getSubnetRequest, Subnet.LifecycleState.Terminated)
                    .execute();

            log.info("Deleted Subnet: [{}]", subnet.getId());
            log.info("");
        } catch (Exception e) {
            log.warn("delete subnet fail error");
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

    private Instance createInstance(ComputeWaiters computeWaiters, LaunchInstanceDetails launchInstanceDetails)
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

    public static String findRootCompartment(IdentityClient identityClient, String tenantId) {
        // 使用`compartmentIdInSubtree`参数来获取所有子区间
        ListCompartmentsRequest request = ListCompartmentsRequest.builder()
                .compartmentId(tenantId)
                .compartmentIdInSubtree(true)
                .accessLevel(ListCompartmentsRequest.AccessLevel.Accessible)
                .build();

        ListCompartmentsResponse response = identityClient.listCompartments(request);
        List<Compartment> compartments = response.getItems();

        // 根区间是没有parentCompartmentId的区间
        for (Compartment compartment : compartments) {
            if (compartment.getCompartmentId().equals(tenantId) && compartment.getId().equals(compartment.getCompartmentId())) {
                return compartment.getId(); // 返回根区间ID
            }
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
            if (existingPublicIpAddress != null) {
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
            releaseUnusedPublicIps();
            log.error("【更换公共IP】用户：[{}] ，区域：[{}] 更换IP任务异常，稍后将重试......", user.getUsername(), user.getOciCfg().getRegion());
        }
        return null;
    }

    public OciCfgDetailsRsp.InstanceInfo getInstanceInfo(String instanceId) {
        Instance instance = getInstanceById(instanceId);
        String bootVolumeSize = null;
        try {
            List<AvailabilityDomain> availabilityDomains = getAvailabilityDomains(identityClient, compartmentId);
            for (AvailabilityDomain availabilityDomain : availabilityDomains) {
                GetInstanceConfigurationRequest.builder()
                        .instanceConfigurationId(instance.getInstanceConfigurationId())
                        .build();
                List<String> BootVolumeIdList = computeClient.listBootVolumeAttachments(ListBootVolumeAttachmentsRequest.builder()
                        .availabilityDomain(availabilityDomain.getName())
                        .compartmentId(compartmentId)
                        .instanceId(instance.getId())
                        .build()).getItems()
                        .stream().map(BootVolumeAttachment::getBootVolumeId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                if (CollectionUtil.isNotEmpty(BootVolumeIdList)) {
                    GetBootVolumeRequest getBootVolumeRequest = GetBootVolumeRequest.builder()
                            .bootVolumeId(BootVolumeIdList.get(0))
                            .build();
                    GetBootVolumeResponse getBootVolumeResponse = blockstorageClient.getBootVolume(getBootVolumeRequest);
                    BootVolume bootVolume = getBootVolumeResponse.getBootVolume();
                    bootVolumeSize = bootVolume.getSizeInGBs() + "";
                    break;
                }
            }
        } catch (Exception e) {
            log.error("用户：[{}] ，区域：[{}] ， 实例：[{}] 的引导卷不存在~",
                    user.getUsername(), user.getOciCfg().getRegion(),
                    instance.getDisplayName());
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
                .build();
    }

    private String updateInstanceState(String instanceId, InstanceActionEnum action) {
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

    private void terminateInstance(String instanceId, boolean preserveBootVolume, boolean preserveDataVolumesCreatedAtLaunch) {
        TerminateInstanceRequest terminateInstanceRequest = TerminateInstanceRequest.builder()
                .instanceId(instanceId)
//                .ifMatch("EXAMPLE-ifMatch-Value")
                .preserveBootVolume(preserveBootVolume) // 是否删除或保留引导卷 ，默认false不保留
                .preserveDataVolumesCreatedAtLaunch(preserveDataVolumesCreatedAtLaunch) // 是否删除或保留启动期间创建的数据卷，默认true保留
                .build();

        /* Send request to the Client */
        TerminateInstanceResponse response = computeClient.terminateInstance(terminateInstanceRequest);
    }

    public Vnic getVnicByInstanceId(String instanceId) {
        List<Vnic> vnics = listInstanceIPs(instanceId);
        if (vnics.isEmpty()) {
            return null;
        }
        return vnics.get(0);
    }

    public Vcn getVcnById(String vcnId) {
        if (vcnId == null) {
            return null;
        }
        for (Vcn vcn : listVcn()) {
            if (vcn.getId().equals(vcnId)) {
                return vcn;
            }
        }
        return null;
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
        Subnet subnet;
        InternetGateway gateway;

        // 添加ipv6 cidr 前缀
        List<String> oldIpv6CidrBlocks = vcn.getIpv6CidrBlocks();
        System.out.println(oldIpv6CidrBlocks);
        if (oldIpv6CidrBlocks == null || oldIpv6CidrBlocks.isEmpty()) {
            virtualNetworkClient.addIpv6VcnCidr(AddIpv6VcnCidrRequest.builder()
                    .vcnId(vcnId)
                    .addVcnIpv6CidrDetails(AddVcnIpv6CidrDetails.builder()
                            .isOracleGuaAllocationEnabled(true)
                            .ipv6PrivateCidrBlock(IPV6_CIDR_BLOCK)
                            .build())
                    .build());
        }

        // 子网
        List<Subnet> oldSubnet = listSubnets(vcnId);
        if (oldSubnet.isEmpty()) {
            try {
                log.warn("用户：[{}] ，区域：[{}] 正在创建子网~", user.getUsername(), user.getOciCfg().getRegion());
                subnet = createSubnet(virtualNetworkClient,
                        compartmentId,
                        getAvailabilityDomains(identityClient, compartmentId).get(0),
                        CIDR_BLOCK, vcn);
            } catch (Exception e) {
                log.error("用户：[{}] ，区域：[{}] 创建子网失败，原因：[{}]", user.getUsername(), user.getOciCfg().getRegion(), e.getLocalizedMessage());
                throw new OciException(-1, "创建子网失败");
            }
        } else {
            subnet = oldSubnet.get(0);
        }

        if (subnet.getIpv6CidrBlocks().isEmpty()) {
            virtualNetworkClient.updateSubnet(UpdateSubnetRequest.builder()
                    .subnetId(subnet.getId())
                    .updateSubnetDetails(UpdateSubnetDetails.builder()
                            .ipv6CidrBlocks(Collections.singletonList(SUBNET_V6_CIDR))
                            .build())
                    .build());
        }
        System.out.println(subnet);


        ListInternetGatewaysResponse listInternetGatewaysResponse = virtualNetworkClient.listInternetGateways(
                ListInternetGatewaysRequest.builder()
                        .compartmentId(compartmentId)
                        .vcnId(vcnId)
                        .limit(1)
                        .build());
        if (!listInternetGatewaysResponse.getItems().isEmpty()) {
            gateway = listInternetGatewaysResponse.getItems().get(0);
        } else {
            try {
                gateway = createInternetGateway(virtualNetworkClient, compartmentId, vcn);
            } catch (Exception e) {
                log.error("用户：[{}] ，区域：[{}] 创建网关失败，原因：[{}]", user.getUsername(), user.getOciCfg().getRegion(), e.getLocalizedMessage());
                throw new OciException(-1, "创建网关失败");
            }
        }

//        System.out.println(gateway);

        // 更新路由表（默认存在）
        List<RouteRule> routeRules = new ArrayList<>();
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
        GetRouteTableRequest getRouteTableRequest = GetRouteTableRequest.builder().rtId(vcn.getDefaultRouteTableId()).build();
        GetRouteTableResponse getRouteTableResponse = virtualNetworkClient.getRouteTable(getRouteTableRequest);
        RouteTable routeTable = getRouteTableResponse.getRouteTable();
        if (routeTable.getRouteRules().isEmpty()) {
            routeRules.add(v4Route);
            routeRules.add(v6Route);
        } else {
            for (RouteRule rule : routeTable.getRouteRules()) {
                if (!rule.getDescription().contains(v4Route.getDescription())) {
                    routeRules.add(v4Route);
                }
                if (!rule.getDescription().contains(v6Route.getDescription())) {
                    routeRules.add(v6Route);
                }
            }
        }

        System.out.println(routeRules);
        UpdateRouteTableRequest updateRouteTableRequest = UpdateRouteTableRequest.builder()
                .rtId(vcn.getDefaultRouteTableId())
                .updateRouteTableDetails(UpdateRouteTableDetails.builder()
                        .routeRules(routeRules)
                        .build())
                .build();
        virtualNetworkClient.updateRouteTable(updateRouteTableRequest);

        // 安全列表（默认存在）
        IngressSecurityRule in = IngressSecurityRule.builder()
                .sourceType(IngressSecurityRule.SourceType.CidrBlock)
                .source("::/0")
                .protocol("all")
                .build();
        EgressSecurityRule out = EgressSecurityRule.builder()
                .destinationType(EgressSecurityRule.DestinationType.CidrBlock)
                .description("::/0")
                .protocol("all")
                .build();
        List<IngressSecurityRule> inList = new ArrayList<>();
        List<EgressSecurityRule> outList = new ArrayList<>();
        GetSecurityListRequest getSecurityListRequest = GetSecurityListRequest.builder().securityListId(vcn.getDefaultSecurityListId()).build();
        GetSecurityListResponse getSecurityListResponse = virtualNetworkClient.getSecurityList(getSecurityListRequest);
        List<IngressSecurityRule> ingressSecurityRules = getSecurityListResponse.getSecurityList().getIngressSecurityRules();
        if (ingressSecurityRules == null || ingressSecurityRules.isEmpty()) {
            inList.add(in);
        } else {
            for (IngressSecurityRule rule : ingressSecurityRules) {
                if (!rule.getSource().contains(in.getSource())) {
                    inList.add(in);
                }
            }
        }
        List<EgressSecurityRule> egressSecurityRules = getSecurityListResponse.getSecurityList().getEgressSecurityRules();
        if (egressSecurityRules == null || egressSecurityRules.isEmpty()) {
            outList.add(out);
        } else {
            for (EgressSecurityRule rule : egressSecurityRules) {
                if (!rule.getDescription().contains(out.getDescription())) {
                    outList.add(out);
                }
            }
        }
        virtualNetworkClient.updateSecurityList(UpdateSecurityListRequest.builder()
                .securityListId(vcn.getDefaultSecurityListId())
                .updateSecurityListDetails(UpdateSecurityListDetails.builder()
                        .ingressSecurityRules(inList)
                        .egressSecurityRules(outList)
                        .build())
                .build());

//        System.out.println("网络安全组更新完成");
//
//        virtualNetworkClient.updateInternetGateway(UpdateInternetGatewayRequest.builder()
//                .igId(gateway.getId())
//                .updateInternetGatewayDetails(UpdateInternetGatewayDetails.builder()
//                        .isEnabled(true)
//                        .routeTableId(vcn.getDefaultRouteTableId())
//                        .build())
//                .build());
//        System.out.println(gateway);

        CreateIpv6Response createIpv6Response = virtualNetworkClient.createIpv6(CreateIpv6Request.builder()
                .createIpv6Details(CreateIpv6Details.builder()
                        .vnicId(vnic.getId())
                        .build())
                .build());

        return createIpv6Response.getIpv6();
    }
}
