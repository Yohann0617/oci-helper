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
3. 多租户同时批量开机，后台一直运行，直至开机成功。
4. 支持断点续抢，配置以及抢机任务都保存在本地数据库，服务重启会继续执行抢机任务，无需重复配置。
5. 支持多区号（配置项以`region`区分），例：我有一个4区号，则新增4个配置，修改`region`即可，其他配置项都一样。

## 一键 docker-compose 部署或更新

安装完成后浏览器直接`ip:port`即可访问，账号密码默认都是：`yohann`，如需修改请更改`application.yml`中的配置并执行`docker restart oci-helper`重启docker容器即可。

```bash
bash <(wget -qO- https://github.com/Yohann0617/oci-helper/releases/latest/download/sh_oci-helper_install.sh)
```

此命令也可以用于更新镜像并重启容器，不会删除已有的配置。

## 手动部署

### 1. 新建目录

创建密钥文件存放目录，存放从甲骨文云控制台生成API时下载的`密钥文件.pem`，新增oci配置时只需输入`密钥文件名称.pem`即可，默认会加上这个目录全路径。

```bash
mkdir -p /app/oci-helper/keys && cd /app/oci-helper
```

### 2. 下载文件

1. 下载`Releases`中最新的`application.yml`、`oci-helper.db`这两个文件到`/app/oci-helper`目录下，并修改`application.yml`部分配置。
2. 如不使用 docker 部署则再下载一个`ocihelper-0.0.1.jar`文件到`/app/oci-helper`目录下，直接`nohup java -jar ocihelper-0.0.1.jar &`运行即可（前提是环境上要有`jre8`或`jdk8`以上的环境）。

### 3. docker部署

需提前安装docker环境，支持arm64、amd64架构。

#### 3.1 方式一

docker直接运行：

```bash
docker run -d --name oci-helper --restart=always \
-p 8818:8818 \
-v /app/oci-helper/application.yml:/app/oci-helper/application.yml \
-v /app/oci-helper/oci-helper.db:/app/oci-helper/oci-helper.db \
-v /app/oci-helper/keys:/app/oci-helper/keys \
ghcr.io/yohann0617/oci-helper:master
```

#### 3.2 方式二

下载`Releases`中最新的`docker-compose.yml`到`/app/oci-helper`目录下，运行以下命令：

```bash
docker compose up -d
```

更新最新镜像：

```bash
docker compose pull && docker compose up -d
```

## 页面展示

![image.png](https://pic2.58cdn.com.cn/nowater/webim/big/n_v2dbe45607168944718bb0ccb5e53b41f8.png)

![image.png](https://pic4.58cdn.com.cn/nowater/webim/big/n_v290443ddeb885445399561ab6eb1d7a09.png)

![image.png](https://pic1.58cdn.com.cn/nowater/webim/big/n_v2543323ea3d274c2ca435e2b5dcc3074f.png)

![image.png](https://pic3.58cdn.com.cn/nowater/webim/big/n_v2e3c93ccfcbd6442b8093d11fec370ee1.png)

![image.png](https://pic1.58cdn.com.cn/nowater/webim/big/n_v26273e166fd944c5e98a665020c798f95.png)

![image.png](https://pic2.58cdn.com.cn/nowater/webim/big/n_v2520fa8e9b66a4cb192ce26a177dd0133.png)

## Stargazers over time

[![Stargazers over time](https://starchart.cc/Yohann0617/oci-helper.svg)](https://starchart.cc/Yohann0617/oci-helper)
