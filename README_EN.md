[ZH](README.md) | [EN](README_EN.md)

# oci-helper

> A web-based visual Oracle Cloud Assistant developed based on Oracle OCI SDK. Currently implemented functions include: support for adding multiple tenant configurations, querying tenant instance information, changing instance public IP according to multiple CIDR network segments, multi-tenant gun machine and other functions.

## Core functions

1. Manage multiple tenant configuration information at the same time.
2. Change instance public IP according to multiple CIDR network segments. If there are abnormalities such as frequent requests, they will be ignored directly and will not affect the next execution until the IP of the specified IP segment is changed.
3. Multiple tenants are started at the same time, and the background keeps running until the startup is successful.
4. Support breakpoint continuation, the gun machine task is saved in the database, the service restart will continue to execute the machine task, no need to repeat the configuration.

## How to deploy

Docker deployment is recommended. For security reasons, it is recommended to use Nginx reverse proxy to configure HTTPS access.

### 1. Create a new directory
Create a directory to store key files. When adding oci configuration, you only need to enter the key file name. The full path of this directory will be added by default.
```bash
mkdir -p /app/oci-helper/keys
```

### 2. Copy files

Copy `application.yml` and `oci-helper.db` to the `/app/oci-helper` directory, and modify some configurations of `application.yml`.

### 3. Pull the image from docker and run it directly

The docker environment needs to be installed in advance. The image size is about 170MB and supports arm64 and amd64 architectures.

```bash
docker run -d --name oci-helper --restart=always \
-p 8818:8818 \
-v /app/oci-helper/application.yml:/app/oci-helper/application.yml \
-v /app/oci-helper/oci-helper.db:/app/oci-helper/oci-helper.db \
-v /app/oci-helper/keys:/app/oci-helper/keys \
ghcr.io/yohann0617/oci-helper:master 
``` 

## Page display 

![image.png](https://pic1.58cdn.com.cn/nowater/webim/big/n_v26a2f3e2cd0ea4ac787723191f4f32f36.png)

![image.png](https://pic4.58cdn.com.cn/nowater/webim/big/n_v290443ddeb885445399561ab6eb1d7a09.png)

![image.png](https://pic1.58cdn.com.cn/nowater/webim/big/n_v2543323ea3d274c2ca435e2b5dcc3074f.png)

![image.png](https://pic3.58cdn.com.cn/nowater/webim/big/n_v2e3c93ccfcbd6442b8093d11fec370ee1.png)

![image.png](https://pic7.58cdn.com.cn/nowater/webim/big/n_v2a47b5866e28344e695b25a84f568ba05.png)
