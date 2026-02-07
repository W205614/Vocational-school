package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.remark.RemarkClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionReplyService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.tianji.common.constants.Constant.DATA_FIELD_NAME_CREATE_TIME;
import static com.tianji.common.constants.Constant.DATA_FIELD_NAME_LIKED_TIME;

/**
 * <p>
 * 互动问题的回答或评论 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2026-02-04
 */
@Service
@RequiredArgsConstructor
public class InteractionReplyServiceImpl extends ServiceImpl<InteractionReplyMapper, InteractionReply> implements IInteractionReplyService {

    private final InteractionQuestionMapper questionMapper;
    private final UserClient userClient;
    private final RemarkClient remarkClient;

    @Override
    public void saveReply(ReplyDTO dto) {
        //1.获取当前登录用户id
        Long userId = UserContext.getUser();

        //2.保存回答或者评论
        //表：interaction_reply
        InteractionReply reply = BeanUtils.copyBean(dto, InteractionReply.class);
        reply.setUserId(userId);
        this.save(reply);

        //获取问题实体
        InteractionQuestion question = questionMapper.selectById(dto.getQuestionId());

        //3.判断是否是回答
        //dto.getAnswerId()为空则是回答，不为空则是评论
        if (dto.getAnswerId() != null) {
            //3.1.如果不是回答(评论)，累加回答下的评论次数
            InteractionReply commentInfo = this.getById(dto.getAnswerId());
            commentInfo.setReplyTimes(commentInfo.getReplyTimes() + 1);  //评论次数累加
            this.updateById(commentInfo);
        }else {
            //3.2.如果是回答，修改问题表最近一次回答id，同时累加问题表的回答次数
            question.setLatestAnswerId(reply.getId());
            question.setAnswerTimes(question.getAnswerTimes() + 1);  //问题表的回答次数累加
        }

        if (dto.getIsStudent()) {
            //4.判断是否是学生提交
            //dto.getIsStudent()为true则代表学生提交，如果是将问题表中该问题的status字段改为未查看
            question.setStatus(QuestionStatus.UN_CHECK);
        }
        questionMapper.updateById(question);
    }

    @Override
    public PageDTO<ReplyVO> queryReplyVOPage(ReplyPageQuery query, Boolean isAdmin) {
        // 1.校验questionId和answerId是否都为空
        if (query.getQuestionId() == null && query.getAnswerId() == null) {
            throw new BadRequestException("问题id和回答id不能都为空");
        }

        // 2.分页查询interaction_reply表
        Page<InteractionReply> page = this.lambdaQuery()
                // 如果传问题id则拼接问题id条件
                .eq(query.getQuestionId() != null, InteractionReply::getQuestionId, query.getQuestionId())
                // .eq(query.getAnswerId() != null, InteractionReply::getAnswerId, query.getAnswerId())
                // 如果回答id没传，则查询answer_id为0的数据，也就是回答
                .eq(InteractionReply::getAnswerId, query.getAnswerId() == null ? 0L : query.getAnswerId())
                // 如果不是管理端, 则查询非隐藏的数据
                .eq(!isAdmin, InteractionReply::getHidden, false)
                .page(query.toMpPage(// 先根据点赞数排序，点赞数相同，再按照创建时间排序
                        new OrderItem(DATA_FIELD_NAME_LIKED_TIME, false),
                        new OrderItem(DATA_FIELD_NAME_CREATE_TIME, true)));
        List<InteractionReply> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(0L, 0L);
        }

        // 3.补全其他数据
        Set<Long> uids = new HashSet<>();  //所有用户id集合
        Set<Long> targetReplyIds = new HashSet<>();  //回复的目标回复id集合
        Set<Long> bizIds = new HashSet<>();
        for (InteractionReply record : records) {
            if (!record.getAnonymity() || isAdmin) {
                uids.add(record.getUserId());  //当前回复者id
                uids.add(record.getTargetUserId());  //目标用户id
            }
            if (record.getTargetReplyId() != null && record.getTargetReplyId() > 0) {
                targetReplyIds.add(record.getTargetReplyId());
            }
            bizIds.add(record.getId());
        }

        // 4.查询目标回复，如果目标回复不是匿名
        if (targetReplyIds.size() > 0) {
            List<InteractionReply> targetReplies = listByIds(targetReplyIds);
            Set<Long> targetUserIds = targetReplies.stream()
                    // Predicate.not是排除某个字段, 这里是排除匿名的目标回复
                    // 过滤出所有非匿名回复, 或者管理端可以看到所有回复
                    .filter(Predicate.not(InteractionReply::getAnonymity).or(r -> isAdmin))
                    .map(InteractionReply::getUserId)
                    .collect(Collectors.toSet());
            uids.addAll(targetUserIds);
        }

        // 5.远程调用用户服务，查询所有用户信息
        List<UserDTO> userDTOList = userClient.queryUserByIds(uids);
        Map<Long, UserDTO> userDTOMap = new HashMap<>();
        if (userDTOList != null) {
            userDTOMap = userDTOList.stream().collect(Collectors.toMap(UserDTO::getId, c -> c));
        }
        Set<Long> bizLiked = remarkClient.isBizLiked(bizIds);

        // 6.封装vo返回
        List<ReplyVO> voList = new ArrayList<>();
        for (InteractionReply record : records) {
            ReplyVO vo = BeanUtils.copyBean(record, ReplyVO.class);
            if (!record.getAnonymity() || isAdmin) {
                UserDTO userDTO = userDTOMap.get(record.getUserId());
                if (userDTO != null) {
                    vo.setUserName(userDTO.getName());  //当前回复者名字
                    vo.setUserIcon(userDTO.getIcon());  //当前回复者头像
                    vo.setUserType(userDTO.getType());  //当前回复者类型
                }
            }
            UserDTO targetUserDTO = userDTOMap.get(record.getTargetReplyId());
            if (targetUserDTO != null) {
                vo.setTargetUserName(targetUserDTO.getName());  //目标用户名字
            }
            vo.setLiked(bizLiked.contains(record.getId()));
            voList.add(vo);
        }
        return PageDTO.of(page, voList);
    }

    @Transactional
    @Override
    public void hiddenReplyAdmin(Long id, Boolean hidden) {
        //1.根据id查询回答或评论
        InteractionReply reply = this.getById(id);
        if (reply == null) {
            throw new BadRequestException("要隐藏或显示的回答或评论不存在");
        }

        //2.显示或隐藏
        reply.setHidden(hidden);
        this.updateById(reply);

        //3.判断是评论还是回答，如果是回答，则需要隐藏或显示回答下的评论
        if (reply.getAnswerId() != 0  && reply.getAnswerId() > 0) {
            //3.1.说明自己是评论，无需处理
            return;
        }
        //3.2.说明自己是回答，需要隐藏或显示回答下的评论
        this.lambdaUpdate()
                .set(InteractionReply::getHidden, hidden)
                .eq(InteractionReply::getAnswerId, id)
                .update();
    }

    @Override
    public ReplyVO queryReplyVOByIdAdmin(Long id) {
        // 1.根据id查询
        InteractionReply r = getById(id);

        // 2.数据处理，需要查询用户信息、评论目标信息、当前用户是否点赞
        Set<Long> userIds = new HashSet<>();
        // 2.1.获取用户 id
        userIds.add(r.getUserId());
        // 2.2.查询评论目标，如果评论目标不是匿名，则需要查询出目标回复的用户id
        if(r.getTargetReplyId() != null && r.getTargetReplyId() != 0) {
            InteractionReply target = getById(r.getTargetReplyId());
            if(!target.getAnonymity()) {
                userIds.add(target.getUserId());
            }
        }
        // 2.3.查询用户详细
        Map<Long, UserDTO> userMap = new HashMap<>(userIds.size());
        if(userIds.size() > 0) {
            List<UserDTO> users = userClient.queryUserByIds(userIds);
            userMap = users.stream().collect(Collectors.toMap(UserDTO::getId, u -> u));
        }
        // 2.4.查询用户点赞状态
        Set<Long> bizLiked = remarkClient.isBizLiked(CollUtils.singletonList(id));

        // 3.处理VO
        // 3.1.拷贝基础属性
        ReplyVO v = BeanUtils.toBean(r, ReplyVO.class);
        // 3.2.回复人信息
        UserDTO userDTO = userMap.get(r.getUserId());
        if (userDTO != null) {
            v.setUserIcon(userDTO.getIcon());
            v.setUserName(userDTO.getName());
            v.setUserType(userDTO.getType());
        }
        // 3.3.目标用户
        UserDTO targetUser = userMap.get(r.getTargetUserId());
        if (targetUser != null) {
            v.setTargetUserName(targetUser.getName());
        }
        // 3.4.点赞状态
        v.setLiked(bizLiked.contains(id));
        return v;
    }
}
