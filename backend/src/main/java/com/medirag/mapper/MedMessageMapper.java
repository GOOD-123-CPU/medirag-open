package com.medirag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medirag.entity.MedMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息 Mapper
 */
@Mapper
public interface MedMessageMapper extends BaseMapper<MedMessage> {
}
