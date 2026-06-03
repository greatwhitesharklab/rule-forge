package com.ruleforge.console.controller;

import com.ruleforge.Configure;
import com.ruleforge.exception.RuleException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;

public class BaseController {
    private final ObjectMapper mapper;

    public BaseController() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.setDateFormat(new SimpleDateFormat(Configure.getDateFormat()));
        this.mapper = mapper;
    }

    protected Element parseXml(InputStream stream) {
        SAXReader reader = new SAXReader();
        Document document;
        try {
            document = reader.read(stream);
            return document.getRootElement();
        } catch (DocumentException e) {
            throw new RuleException(e);
        }
    }

    protected String writeObjectToJson(Object obj) throws IOException {
        return this.mapper.writeValueAsString(obj);
    }
}
