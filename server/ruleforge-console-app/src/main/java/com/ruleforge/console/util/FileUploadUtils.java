package com.ruleforge.console.util;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * 文件上传工具类
 */
public class FileUploadUtils {

    /**
     * 上传文件到临时服务器
     *
     * @param importFile 要上传的文件
     * @param tmpUrl 临时服务器URL
     * @return 上传后的文件路径
     * @throws IOException 如果文件上传过程中发生IO异常
     */
    public static String uploadFileToTempServer(MultipartFile importFile, String tmpUrl) throws IOException {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
        map.add("file", new ByteArrayResource(importFile.getBytes()) {
            @Override
            public String getFilename() {
                return importFile.getOriginalFilename();
            }
        });
        map.add("group", "common");
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.add("xblade-token", "b82df382fda847f3a82ec5807b61817d");
        HttpEntity<MultiValueMap<String, Object>> param = new HttpEntity<>(map, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(tmpUrl, param, Map.class);
        
        return (String) response.getBody().get("data");
    }
}