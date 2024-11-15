[ZH](README.md) | [EN](README_EN.md)

# oci-helper

> 一个基于 Oracle OCI SDK 开发的WEB端可视化甲骨文云助手，目前实现的功能有：支持添加多个租户配置、查询租户实例信息、根据多个CIDR网段更换实例公共IP、多租户抢机、断点续抢等功能。

## 注意事项及免责声明
- 因开机、换IP频率过高而导致的封号本人概不负责。
- 开发此项目纯属个人爱好，无后门，放心使用。
- 强烈建议不要裸HTTP访问，应使用Nginx反向代理配置HTTPS访问。
- 建议使用密钥登录服务器，防止服务器被SSH爆破导致API数据及密钥泄露。

## 核心功能

1. 同时管理多个租户配置信息，支持模糊搜索、状态筛选。
2. 根据多个CIDR网段更换实例公共IP，遇到请求频繁等异常会直接忽略，不影响下一次执行，直至更换到指定IP段的IP。
3. 多租户同时开机，后台一直运行，直至开机成功。
4. 支持断点续抢，配置以及抢机任务都保存在本地数据库，服务重启会继续执行抢机任务，无需重复配置。

## 如何部署

### 1. 新建目录
创建密钥文件存放目录，新增oci配置时只需输入密钥文件名称即可，默认会加上这个目录全路径。
```bash
mkdir -p /app/oci-helper/keys && cd /app/oci-helper
```

### 2. 下载文件

1. 下载`Releases`中最新的`application.yml`、`oci-helper.db`这两个文件到`/app/oci-helper`目录下，并修改`application.yml`部分配置。
2. 如不使用 docker 部署则再下载一个`ocihelper-0.0.1.jar`文件到`/app/oci-helper`目录下，直接`nohup java -jar ocihelper-0.0.1.jar &`运行即可（前提是环境上要有`jre8`或`jdk8`以上的环境）。

### 3. docker部署

需提前安装docker环境，支持arm64、amd64架构。

### 3.1 方式一

docker直接运行：

```bash
docker run -d --name oci-helper --restart=always \
-p 8818:8818 \
-v /app/oci-helper/application.yml:/app/oci-helper/application.yml \
-v /app/oci-helper/oci-helper.db:/app/oci-helper/oci-helper.db \
-v /app/oci-helper/keys:/app/oci-helper/keys \
ghcr.io/yohann0617/oci-helper:master
```

### 3.2 方式二

下载`Releases`中最新的`docker-compose.yml`到`/app/oci-helper`目录下，运行以下命令：

```bash
docker compose up -d
```

更新最新镜像：

```bash
docker compose pull && docker compose up -d
```


## 页面展示

![image.png](https://pic1.58cdn.com.cn/nowater/webim/big/n_v26a2f3e2cd0ea4ac787723191f4f32f36.png)

![image.png](https://pic4.58cdn.com.cn/nowater/webim/big/n_v290443ddeb885445399561ab6eb1d7a09.png)

![image.png](https://pic1.58cdn.com.cn/nowater/webim/big/n_v2543323ea3d274c2ca435e2b5dcc3074f.png)

![image.png](https://pic3.58cdn.com.cn/nowater/webim/big/n_v2e3c93ccfcbd6442b8093d11fec370ee1.png)

![image.png](https://pic7.58cdn.com.cn/nowater/webim/big/n_v2a47b5866e28344e695b25a84f568ba05.png)

