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
import com.yohann.ocihelper.enums.ArchitectureEnum;
import com.yohann.ocihelper.enums.ErrorEnum;
import com.yohann.ocihelper.enums.InstanceStateEnum;
import com.yohann.ocihelper.enums.OperationSystemEnum;
import com.yohann.ocihelper.exception.OciException;
import com.yohann.ocihelper.utils.CommonUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.oracle.bmc.core.model.CreatePublicIpDetails.Lifetime.Ephemeral;
import static com.yohann.ocihelper.service.impl.OciServiceImpl.TEMP_MAP;

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
    private ComputeClient computeClient;
    private final IdentityClient identityClient;
    private final WorkRequestClient workRequestClient;
    private final ComputeWaiters computeWaiters;
    private VirtualNetworkClient virtualNetworkClient;
    private BlockstorageClient blockstorageClient;
    private final String compartmentId;

    @Override
    public void close() throws IOException {
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
                    try {
                        return new FileInputStream(ociCfg.getPrivateKeyPath());
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                        throw new RuntimeException("获取密钥失败");
                    }
                })
                .region(Region.valueOf(ociCfg.getRegion()))
                .build();

        computeClient = ComputeClient.builder().build(provider);
        virtualNetworkClient = VirtualNetworkClient.builder().build(provider);
        blockstorageClient = BlockstorageClient.builder().build(provider);

        identityClient = IdentityClient.builder().build(provider);
        compartmentId = findRootCompartment(identityClient, provider.getTenantId());
        identityClient.setRegion(ociCfg.getRegion());

        computeClient = ComputeClient.builder().build(provider);
        computeClient.setRegion(ociCfg.getRegion());

        workRequestClient = WorkRequestClient.builder().build(provider);
        workRequestClient.setRegion(ociCfg.getRegion());
        computeWaiters = computeClient.newWaiters(workRequestClient);

        virtualNetworkClient = VirtualNetworkClient.builder().build(provider);
        virtualNetworkClient.setRegion(ociCfg.getRegion());

        blockstorageClient = BlockstorageClient.builder().build(provider);
        blockstorageClient.setRegion(ociCfg.getRegion());
    }

    synchronized public InstanceDetailDTO createInstanceData() {
        if (user.getCreateNumbers() <= 0) {
            return null;
        }
        Long currentCount = (Long) TEMP_MAP.compute(
                CommonUtils.CREATE_COUNTS_PREFIX + user.getTaskId(),
                (key, value) -> value == null ? 1L : Long.parseLong(String.valueOf(value)) + 1
        );

        InstanceDetailDTO instanceDetailDTO = new InstanceDetailDTO();
        instanceDetailDTO.setTaskId(user.getTaskId());
        instanceDetailDTO.setUsername(user.getUsername());
        instanceDetailDTO.setLeftCreateNumbers(user.getCreateNumbers());
        instanceDetailDTO.setRegion(user.getOciCfg().getRegion());
        instanceDetailDTO.setArchitecture(user.getArchitecture());
        log.info("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] ，开机数量：[{}] ，开始执行第 [{}] 次创建实例操作......",
                user.getUsername(), user.getOciCfg().getRegion(), user.getArchitecture(), user.getCreateNumbers(), currentCount);
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
//                    log.info("---------------- AvailabilityDomain: {} ----------------", availableDomain.getName());
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

                        log.info("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] 开机成功，正在为实例创建引导卷......",
                                user.getUsername(), user.getOciCfg().getRegion(), user.getArchitecture());
//                        log.info("-----------------------------------------------------------------");
                        //bootVolume = createBootVolume(blockstorageClient, compartmentId, availablityDomain, image, kmsKeyId);
//                        launchInstanceDetails = createLaunchInstanceDetailsFromBootVolume(launchInstanceDetails, bootVolume);
                        //instanceFromBootVolume = createInstance(computeWaiters, launchInstanceDetails);
                        //printInstance(computeClient, virtualNetworkClient, instanceFromBootVolume, oracleInstanceDetail);
                        user.setCreateNumbers(user.getCreateNumbers() - 1);
                        instanceDetailDTO.setSuccess(true);
                        instanceDetailDTO.setLeftCreateNumbers(user.getCreateNumbers());
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
                        if (error.getStatusCode() == 500 &&
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
                                    user.getUsername(), user.getOciCfg().getRegion(), user.getArchitecture());
                            return instanceDetailDTO;
                        } else if (error.getStatusCode() == 429 && error.getMessage().contains(ErrorEnum.TOO_MANY_REQUESTS.getErrorType())) {
                            log.warn("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] 开机请求频繁，[{}]秒后将重试......",
                                    user.getUsername(), user.getOciCfg().getRegion(), user.getArchitecture(), user.getInterval());
                        } else {
//                            clearAllDetails(computeClient, virtualNetworkClient, instanceFromBootVolume, instance, networkSecurityGroup,
//                             internetGateway, subnet, vcn);
                            instanceDetailDTO.setOut(true);
                            log.warn("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] 出现错误了,原因为:{}",
                                    user.getUsername(), user.getOciCfg().getRegion(), user.getArchitecture(),
                                    e.getMessage(), e);
                            return instanceDetailDTO;
                        }
                    } else {
                        //clearAllDetails(computeClient, virtualNetworkClient, instanceFromBootVolume, instance, networkSecurityGroup,
                        // internetGateway, subnet, vcn);
                        instanceDetailDTO.setOut(true);
                        log.warn("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] 出现错误了,原因为:{}",
                                user.getUsername(), user.getOciCfg().getRegion(), user.getArchitecture(),
                                e.getMessage(), e);
                        return instanceDetailDTO;
                    }
                }
            }
        } catch (Exception e) {
            instanceDetailDTO.setOut(true);
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

        return response.getItems().stream()
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
//                System.out.println("  Private IP: " + vnic.getPrivateIp());
//                if (vnic.getPublicIp() != null) {
//                    System.out.println("  Public IP: " + vnic.getPublicIp());
//                    return vnic;
//                }
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
            return "10.0.0.0/16";
        }
        if (log.isDebugEnabled()) {
            // 遍历所有 VCN 并打印其 CIDR 块
            for (Vcn vcn : listVcnsResponse.getItems()) {
                log.info("VCN Name: {}", vcn.getDisplayName());
                log.info("VCN ID: {}", vcn.getId());
                log.info("CIDR Block: {}", vcn.getCidrBlock());

                // 如果 VCN 有多个 CIDR 块，也打印出来
                List<String> cidrBlocks = vcn.getCidrBlocks();
                if (cidrBlocks != null && !cidrBlocks.isEmpty()) {
                    log.info("Additional CIDR Blocks: {}", cidrBlocks);
                }
            }
        }
        return listVcnsResponse.getItems().get(0).getCidrBlock();
    }

    private static List<AvailabilityDomain> getAvailabilityDomains(
            IdentityClient identityClient, String compartmentId) {
        ListAvailabilityDomainsResponse listAvailabilityDomainsResponse =
                identityClient.listAvailabilityDomains(ListAvailabilityDomainsRequest.builder()
                        .compartmentId(compartmentId)
                        .build());
        return listAvailabilityDomainsResponse.getItems();
    }

    private static List<Shape> getShape(
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

                if (log.isDebugEnabled()) {
                    log.info("Found Shape: " + vmShape.getShape());
                    log.info("Billing Type: " + vmShape.getBillingType());
                    log.info("<====================================>");
                }

            }
        }
        return shapesNewList;
    }

    private static Image getImage(ComputeClient computeClient, String compartmentId, Shape shape, SysUserDTO user) {
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
        Image image = images.get(0);

        return image;
    }

    private static Vcn createVcn(
            VirtualNetworkClient virtualNetworkClient, String compartmentId, String cidrBlock)
            throws Exception {
        String vcnName = "java-sdk-example-vcn";
        ListVcnsRequest build = ListVcnsRequest.builder().compartmentId(compartmentId)
                .displayName(vcnName)
                .build();

        ListVcnsResponse listVcnsResponse = virtualNetworkClient.listVcns(build);
        if (listVcnsResponse.getItems().size() > 0) {
            return listVcnsResponse.getItems().get(0);
        }
        CreateVcnDetails createVcnDetails =
                CreateVcnDetails.builder()
                        .cidrBlock(cidrBlock)
                        .compartmentId(compartmentId)
                        .displayName(vcnName)
                        .build();

        CreateVcnRequest createVcnRequest =
                CreateVcnRequest.builder().createVcnDetails(createVcnDetails).build();
        CreateVcnResponse createVcnResponse = virtualNetworkClient.createVcn(createVcnRequest);

        GetVcnRequest getVcnRequest =
                GetVcnRequest.builder().vcnId(createVcnResponse.getVcn().getId()).build();
        GetVcnResponse getVcnResponse =
                virtualNetworkClient
                        .getWaiters()
                        .forVcn(getVcnRequest, Vcn.LifecycleState.Available)
                        .execute();
        Vcn vcn = getVcnResponse.getVcn();

        log.info("Created Vcn: " + vcn.getId());

        return vcn;
    }

    private static void deleteVcn(VirtualNetworkClient virtualNetworkClient, Vcn vcn)
            throws Exception {
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

//        for (Vcn vcn : response.getItems()) {
//            System.out.println("VCN Name: " + vcn.getDisplayName() + ", OCID: " + vcn.getId());
//        }
        return response.getItems();
    }

    private static InternetGateway createInternetGateway(
            VirtualNetworkClient virtualNetworkClient, String compartmentId, Vcn vcn)
            throws Exception {
        String internetGatewayName = "java-sdk-example-internet-gateway";

        //查询网关是否存在,不存在再创建
        ListInternetGatewaysRequest build = ListInternetGatewaysRequest.builder()
                .compartmentId(compartmentId)
                .displayName(internetGatewayName)
                .build();

        ListInternetGatewaysResponse listInternetGatewaysResponse = virtualNetworkClient.listInternetGateways(build);
        if (listInternetGatewaysResponse.getItems().size() > 0) {
            return listInternetGatewaysResponse.getItems().get(0);
        }

        CreateInternetGatewayDetails createInternetGatewayDetails =
                CreateInternetGatewayDetails.builder()
                        .compartmentId(compartmentId)
                        .displayName(internetGatewayName)
                        .isEnabled(true)
                        .vcnId(vcn.getId())
                        .build();
        CreateInternetGatewayRequest createInternetGatewayRequest =
                CreateInternetGatewayRequest.builder()
                        .createInternetGatewayDetails(createInternetGatewayDetails)
                        .build();
        CreateInternetGatewayResponse createInternetGatewayResponse =
                virtualNetworkClient.createInternetGateway(createInternetGatewayRequest);

        GetInternetGatewayRequest getInternetGatewayRequest =
                GetInternetGatewayRequest.builder()
                        .igId(createInternetGatewayResponse.getInternetGateway().getId())
                        .build();
        GetInternetGatewayResponse getInternetGatewayResponse =
                virtualNetworkClient
                        .getWaiters()
                        .forInternetGateway(
                                getInternetGatewayRequest, InternetGateway.LifecycleState.Available)
                        .execute();
        InternetGateway internetGateway = getInternetGatewayResponse.getInternetGateway();

        log.info("Created Internet Gateway: " + internetGateway.getId());

        return internetGateway;
    }

    private static void deleteInternetGateway(
            VirtualNetworkClient virtualNetworkClient, InternetGateway internetGateway)
            throws Exception {
        DeleteInternetGatewayRequest deleteInternetGatewayRequest =
                DeleteInternetGatewayRequest.builder().igId(internetGateway.getId()).build();
        virtualNetworkClient.deleteInternetGateway(deleteInternetGatewayRequest);

        GetInternetGatewayRequest getInternetGatewayRequest =
                GetInternetGatewayRequest.builder().igId(internetGateway.getId()).build();
        virtualNetworkClient
                .getWaiters()
                .forInternetGateway(
                        getInternetGatewayRequest, InternetGateway.LifecycleState.Terminated)
                .execute();

        log.info("Deleted Internet Gateway: " + internetGateway.getId());
    }

    private static void addInternetGatewayToDefaultRouteTable(
            VirtualNetworkClient virtualNetworkClient, Vcn vcn, InternetGateway internetGateway)
            throws Exception {
        GetRouteTableRequest getRouteTableRequest =
                GetRouteTableRequest.builder().rtId(vcn.getDefaultRouteTableId()).build();
        GetRouteTableResponse getRouteTableResponse =
                virtualNetworkClient.getRouteTable(getRouteTableRequest);

        List<RouteRule> routeRules = getRouteTableResponse.getRouteTable().getRouteRules();

        if (log.isDebugEnabled()) {
            log.info("Current Route Rules in Default Route Table");
            log.info("==========================================");
        }


        // 检查是否已有相同的路由规则
        boolean ruleExists = routeRules.stream()
                .anyMatch(rule -> "0.0.0.0/0".equals(rule.getDestination())
                        && rule.getDestinationType() == RouteRule.DestinationType.CidrBlock);

        if (ruleExists) {
            log.info("The route rule for destination 0.0.0.0/0 already exists.");
            return; // 退出方法，不添加新的规则
        }

        // 创建新的路由规则
        RouteRule internetAccessRoute =
                RouteRule.builder()
                        .destination("0.0.0.0/0")
                        .destinationType(RouteRule.DestinationType.CidrBlock)
                        .networkEntityId(internetGateway.getId())
                        .build();

        // 将新的规则添加到新的列表中
        List<RouteRule> updatedRouteRules = new ArrayList<>(routeRules);
        updatedRouteRules.add(internetAccessRoute);

        UpdateRouteTableDetails updateRouteTableDetails =
                UpdateRouteTableDetails.builder().routeRules(updatedRouteRules).build();
        UpdateRouteTableRequest updateRouteTableRequest =
                UpdateRouteTableRequest.builder()
                        .updateRouteTableDetails(updateRouteTableDetails)
                        .rtId(vcn.getDefaultRouteTableId())
                        .build();

        virtualNetworkClient.updateRouteTable(updateRouteTableRequest);

        // 等待路由表更新完成
        getRouteTableResponse =
                virtualNetworkClient
                        .getWaiters()
                        .forRouteTable(getRouteTableRequest, RouteTable.LifecycleState.Available)
                        .execute();
        routeRules = getRouteTableResponse.getRouteTable().getRouteRules();

        if (log.isDebugEnabled()) {
            System.out.println("Updated Route Rules in Default Route Table");
            System.out.println("==========================================");
            routeRules.forEach(System.out::println);
            System.out.println();
        }

    }

    private static void clearRouteRulesFromDefaultRouteTable(
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

    private static Subnet createSubnet(
            VirtualNetworkClient virtualNetworkClient,
            String compartmentId,
            AvailabilityDomain availabilityDomain,
            String networkCidrBlock,
            Vcn vcn)
            throws Exception {
        String subnetName = "java-sdk-example-subnet";
        Subnet subnet = null;
        //检查子网是否存在
        ListSubnetsRequest listRequest = ListSubnetsRequest.builder()
                .compartmentId(compartmentId)
                .vcnId(vcn.getId())
                //.displayName(subnetName)
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

    private static void deleteSubnet(VirtualNetworkClient virtualNetworkClient, Subnet subnet) {
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

    private static NetworkSecurityGroup createNetworkSecurityGroup(
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

    private static void deleteNetworkSecurityGroup(
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

    private static void addNetworkSecurityGroupSecurityRules(
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

    private static void clearNetworkSecurityGroupSecurityRules(
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

    private static Instance createInstance(ComputeWaiters computeWaiters, LaunchInstanceDetails launchInstanceDetails)
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

    private static LaunchInstanceDetails createLaunchInstanceDetails(
            String compartmentId,
            AvailabilityDomain availabilityDomain,
            Shape shape,
            Image image,
            Subnet subnet,
            NetworkSecurityGroup networkSecurityGroup,
            String script,
            SysUserDTO user) {
        String instanceName = System.currentTimeMillis() + "-instance";
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
                .isMonitoringDisabled(false)
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

    private static void terminateInstance(ComputeClient computeClient, Instance instance)
            throws Exception {
        System.out.println("Terminating Instance: " + instance.getId());
        TerminateInstanceRequest terminateInstanceRequest =
                TerminateInstanceRequest.builder().instanceId(instance.getId()).build();
        computeClient.terminateInstance(terminateInstanceRequest);

        GetInstanceRequest getInstanceRequest =
                GetInstanceRequest.builder().instanceId(instance.getId()).build();
        computeClient
                .getWaiters()
                .forInstance(getInstanceRequest, Instance.LifecycleState.Terminated)
                .execute();

        log.info("Terminated Instance: " + instance.getId());
        log.info("-----------------------------------------------------------------");
    }

    private static void printInstance(
            ComputeClient computeClient,
            VirtualNetworkClient virtualNetworkClient,
            Instance instance,
            InstanceDetailDTO instanceDetailDTO) {
        ListVnicAttachmentsRequest listVnicAttachmentsRequest =
                ListVnicAttachmentsRequest.builder()
                        .compartmentId(instance.getCompartmentId())
                        .instanceId(instance.getId())
                        .build();
        ListVnicAttachmentsResponse listVnicAttachmentsResponse =
                computeClient.listVnicAttachments(listVnicAttachmentsRequest);
        List<VnicAttachment> vnicAttachments = listVnicAttachmentsResponse.getItems();
        VnicAttachment vnicAttachment = vnicAttachments.get(0);

        GetVnicRequest getVnicRequest =
                GetVnicRequest.builder().vnicId(vnicAttachment.getVnicId()).build();
        GetVnicResponse getVnicResponse = virtualNetworkClient.getVnic(getVnicRequest);
        Vnic vnic = getVnicResponse.getVnic();

//        log.info("Virtual Network Interface Card :" + vnic.getId());
//        log.info("Public IP :" + vnic.getPublicIp());
//        log.info("Private IP :" + vnic.getPrivateIp());
//        log.info("vnic: [{}]", vnic);
        instanceDetailDTO.setPublicIp(vnic.getPublicIp());
//        InstanceAgentConfig instanceAgentConfig = instance.getAgentConfig();
//        boolean monitoringEnabled =
//                (instanceAgentConfig != null) && !instanceAgentConfig.getIsMonitoringDisabled();
//        String monitoringStatus = (monitoringEnabled ? "Enabled" : "Disabled");
//        log.info("Instance " + instance.getId() + " has monitoring " + monitoringStatus);
    }

    private static BootVolume createBootVolume(
            BlockstorageClient blockstorageClient,
            String compartmentId,
            AvailabilityDomain availabilityDomain,
            Image image,
            String kmsKeyId)
            throws Exception {
        String bootVolumeName = "java-sdk-example-boot-volume";
        // find existing boot volume by image
        ListBootVolumesRequest listBootVolumesRequest =
                ListBootVolumesRequest.builder()
                        .availabilityDomain(availabilityDomain.getName())
                        .compartmentId(compartmentId)
                        .build();
        ListBootVolumesResponse listBootVolumesResponse =
                blockstorageClient.listBootVolumes(listBootVolumesRequest);
        List<BootVolume> bootVolumes = listBootVolumesResponse.getItems();
        String bootVolumeId = null;
        for (BootVolume bootVolume : bootVolumes) {
            if (BootVolume.LifecycleState.Available.equals(bootVolume.getLifecycleState())
                    && image.getId().equals(bootVolume.getImageId())) {
                bootVolumeId = bootVolume.getId();
                break;
            }
        }
        System.out.println("Found BootVolume: " + bootVolumeId);

        // create a new boot volume based on existing one
        BootVolumeSourceDetails bootVolumeSourceDetails =
                BootVolumeSourceFromBootVolumeDetails.builder().id(bootVolumeId).build();
        CreateBootVolumeDetails details =
                CreateBootVolumeDetails.builder()
                        .availabilityDomain(availabilityDomain.getName())
                        .compartmentId(compartmentId)
                        .displayName(bootVolumeName)
                        .sourceDetails(bootVolumeSourceDetails)
                        .kmsKeyId(kmsKeyId)
                        .build();
        CreateBootVolumeRequest createBootVolumeRequest =
                CreateBootVolumeRequest.builder().createBootVolumeDetails(details).build();
        CreateBootVolumeResponse createBootVolumeResponse =
                blockstorageClient.createBootVolume(createBootVolumeRequest);
        System.out.println(
                "Provisioning new BootVolume: " + createBootVolumeResponse.getBootVolume().getId());

        // wait for boot volume to be ready
        GetBootVolumeRequest getBootVolumeRequest =
                GetBootVolumeRequest.builder()
                        .bootVolumeId(createBootVolumeResponse.getBootVolume().getId())
                        .build();
        GetBootVolumeResponse getBootVolumeResponse =
                blockstorageClient
                        .getWaiters()
                        .forBootVolume(getBootVolumeRequest, BootVolume.LifecycleState.Available)
                        .execute();
        BootVolume bootVolume = getBootVolumeResponse.getBootVolume();

        System.out.println("Provisioned BootVolume: " + bootVolume.getId());
        System.out.println(bootVolume);
        System.out.println();

        return bootVolume;
    }

    private static LaunchInstanceDetails createLaunchInstanceDetailsFromBootVolume(
            LaunchInstanceDetails launchInstanceDetails, BootVolume bootVolume) throws Exception {
        String bootVolumeName = "java-sdk-example-instance-from-boot-volume";
        InstanceSourceViaBootVolumeDetails instanceSourceViaBootVolumeDetails =
                InstanceSourceViaBootVolumeDetails.builder()
                        .bootVolumeId(bootVolume.getId())
                        .build();
        LaunchInstanceAgentConfigDetails launchInstanceAgentConfigDetails =
                LaunchInstanceAgentConfigDetails.builder().isMonitoringDisabled(true).build();
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
//                .ipAddress(vnic.getPrivateIp())
                .build();

        ListPrivateIpsResponse privateIpsResponse = virtualNetworkClient.listPrivateIps(listPrivateIpsRequest);

        for (PrivateIp privateIp : privateIpsResponse.getItems()) {
//            System.out.println("Private IP: " + privateIp.getIpAddress() + ", Private IP ID: " + privateIp.getId());
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

}
