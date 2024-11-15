[ZH](README.md) | [EN](README_EN.md)

# oci-helper

> A web-based visual Oracle Cloud Assistant developed based on Oracle OCI SDK. Currently implemented functions include: support for adding multiple tenant configurations, querying tenant instance information, changing instance public IP according to multiple CIDR network segments, multi-tenant grabbing, breakpoint resumption, etc.

## Notes and Disclaimer
- I am not responsible for account suspension due to high frequency of booting and changing IP.
- The development of this project is purely personal hobby, no backdoor, and safe to use.
- It is strongly recommended not to use naked HTTP access, and HTTPS access should be configured using Nginx reverse proxy.
- It is recommended to use a key to log in to the server to prevent the server from being blasted by SSH, resulting in API data and key leakage.

## Core functions

1. Manage multiple tenant configuration information at the same time, support fuzzy search and status filtering.
2. Change the instance public IP according to multiple CIDR network segments. If there are abnormalities such as frequent requests, they will be ignored directly and will not affect the next execution until the IP in the specified IP segment is changed.
3. Multiple tenants start up at the same time, and the background runs until the startup is successful.
4. Support breakpoint continuation. The configuration and machine grabbing tasks are saved in the local database. The machine grabbing tasks will continue to be executed after the service restarts, without repeated configuration.
5. Support multiple region codes (configuration items are distinguished by region). For example: I have a 4-region code, so I add 4 configurations and modify the region. Other configuration items remain the same.

## How to deploy

### 1. Create a new directory
Create a directory to store the key file, and store the `key file.pem` downloaded when generating the API from the Oracle Cloud Console. When adding an oci configuration, you only need to enter the key file name, and the full path of this directory will be added by default.
```bash
mkdir -p /app/oci-helper/keys && cd /app/oci-helper
```

### 2. Download files

1. Download the latest `application.yml` and `oci-helper.db` files in `Releases` to the `/app/oci-helper` directory, and modify some configurations of `application.yml`.
2. If you do not use docker deployment, download another `ocihelper-0.0.1.jar` file to the `/app/oci-helper` directory, and run it directly with `nohup java -jar ocihelper-0.0.1.jar &` (the prerequisite is that the environment must have `jre8` or `jdk8` or above).

### 3. Docker deployment

The docker environment needs to be installed in advance, supporting arm64 and amd64 architectures.

#### 3.1 Method 1

Run directly with docker:

```bash
docker run -d --name oci-helper --restart=always \
-p 8818:8818 \
-v /app/oci-helper/application.yml:/app/oci-helper/application.yml \
-v /app/oci-helper/oci-helper.db:/app/oci-helper/oci-helper.db \
-v /app/oci-helper/keys:/app/oci-helper/keys \
ghcr.io/yohann0617/oci-helper:master
```

#### 3.2 Method 2

Download the latest `docker-compose.yml` in `Releases` to the `/app/oci-helper` directory and run the following command:

```bash
docker compose up -d
```

Update the latest image:

```bash
docker compose pull && docker compose up -d
```

## Page display

![image.png](https://pic1.58cdn.com.cn/nowater/webim/big/n_v26a2f3e2cd0ea4ac787723191f4f32f36.png)

![image.png](https://pic4.58cdn.com.cn/nowater/webim/big/n_v290443ddeb885445399561ab6eb1d7a09.png)

![image.png](https://pic1.58cdn.com.cn/nowater/webim/big/n_v2543323ea3d274c2ca435e2b5dcc3074f.png)

![image.png](https://pic3.58cdn.com.cn/nowater/webim/big/n_v2e3c93ccfcbd6442b8093d11fec370ee1.png)

![image.png](https://pic7.58cdn.com.cn/nowater/webim/big/n_v2a47b5866e28344e695b25a84f568ba05.png)
