package com.yohann.ocihelper.config.auth;

import cn.hutool.jwt.JWTUtil;
import com.yohann.ocihelper.exception.OciException;
import com.yohann.ocihelper.service.IpSecurityService;
import com.yohann.ocihelper.utils.CommonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;

/**
 * @author: Yohann
 * @date: 2024/3/30 18:03
 */
@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Value("${web.password}")
    private String password;
    
    @Autowired
    private IpSecurityService ipSecurityService;

    List<String> noTokenList = Arrays.asList(
            "/api/sys/login",
            "/api/sys/getEnableMfa",
            "/api/sys/googleLogin",
            "/api/sys/getGoogleClientId"
    );


                        @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Check IP security first (blacklist and defense mode)
        String clientIp = getClientIp(request);
        
        if (!ipSecurityService.isIpAllowed(clientIp)) {
            log.warn("IP blocked: {}, URI: {}", clientIp, request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":-1,\"msg\":\"账号或密码不正确\"}");
            return false;
        }
        
        // 放行 WebSocket 握手请求
        if ("GET".equalsIgnoreCase(request.getMethod()) && "websocket".equalsIgnoreCase(request.getHeader("Upgrade"))) {
            return true;
        }

        // 放行预检请求（OPTIONS）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK); // 直接返回 200 状态码
            return true;
        }

        String authorizationHeader = request.getHeader("Authorization");
        if (request.getRequestURI().contains("/api") && !noTokenList.contains(request.getRequestURI())) {
            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                String token = authorizationHeader.substring(7); // 去掉"Bearer "前缀
                // 验证token（这里可以调用你的验证逻辑）
                boolean isValid = validateToken(token);
                if (isValid) {
                    return true; // 继续处理请求
                } else {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    throw new OciException(401, "无权限");
                }
            } else {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                throw new OciException(401, "无权限");
            }
        }

        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }

        private boolean validateToken(String token) {
        return !CommonUtils.isTokenExpired(token) && JWTUtil.verify(token, password.getBytes());
    }
    
    /**
     * Get client IP address (supports proxy headers)
     * 
     * @param request HTTP request
     * @return client IP address
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // X-Forwarded-For may contain multiple IPs, take the first one
            int index = ip.indexOf(',');
            if (index != -1) {
                ip = ip.substring(0, index);
            }
            return ip.trim();
        }
        
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        
        ip = request.getHeader("Proxy-Client-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        
        ip = request.getHeader("WL-Proxy-Client-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        
        return request.getRemoteAddr();
    }
}
