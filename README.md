# oci-helper

> 一个基于 Oracle OCI SDK 开发的WEB端可视化甲骨文云助手，目前实现的功能有：支持添加多个租户配置、查询租户实例信息、根据多个CIDR网段更换实例公共IP、多租户枪机等功能。

## 核心功能

1. 同时管理多个租户配置信息。
2. 根据多个CIDR网段更换实例公共IP，遇到请求频繁等异常会直接忽略，不影响下一次执行，直至更换到指定IP段的IP。
3. 多租户同时开机，后台一直运行，直至开机成功。

## 如何部署

推荐docker部署，出于安全问题建议使用Nginx反向代理配置HTTPS访问。

### 1. 新建目录

```bash
mkdir -p /app/oci-helper/keys
```

### 2.  拷贝文件

复制`application.yml`、`oci-helper.db`到`/app/oci-helper`目录下。

### 3. docker拉取镜像直接运行

需提前安装docker环境，镜像大小约170MB，支持arm64、amd64架构。

```bash
docker run -d --name test --restart=always \
-p 8818:8818 \
-v /app/oci-helper/application.yml:/app/oci-helper/application.yml \
-v /app/oci-helper/oci-helper.db:/app/oci-helper/oci-helper.db \
-v /app/oci-helper/keys:/app/oci-helper/keys \
yohannfan/oci-helper:latest
```

## 页面展示

![image.png](https://pic5.58cdn.com.cn/nowater/webim/big/n_v2095b0fdd8f7e4b7186d265cd261b6d81.png)

![image.png](https://pic1.58cdn.com.cn/nowater/webim/big/n_v2543323ea3d274c2ca435e2b5dcc3074f.png)

![image.png](https://pic3.58cdn.com.cn/nowater/webim/big/n_v2e3c93ccfcbd6442b8093d11fec370ee1.png)