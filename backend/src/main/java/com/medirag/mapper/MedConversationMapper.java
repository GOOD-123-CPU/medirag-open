package com.medirag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medirag.entity.MedConversation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会话 Mapper
 */
@Mapper
public interface MedConversationMapper extends BaseMapper<MedConversation> {
}
