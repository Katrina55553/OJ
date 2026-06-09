package com.oj.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oj.mapper.AiMessageMapper;
import com.oj.model.entity.AiMessage;
import com.oj.service.AiMessageService;
import org.springframework.stereotype.Service;

@Service
public class AiMessageServiceImpl extends ServiceImpl<AiMessageMapper, AiMessage> implements AiMessageService {
}
