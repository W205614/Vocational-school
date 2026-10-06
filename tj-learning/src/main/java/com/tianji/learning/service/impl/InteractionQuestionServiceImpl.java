package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动提问的问题表 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2026-02-04
 */
@Service
@RequiredArgsConstructor
public class InteractionQuestionServiceImpl extends ServiceImpl<InteractionQuestionMapper, InteractionQuestion> implements IInteractionQuestionService {

    private final IInteractionReplyService replyService;
    private final UserClient userClient;
    private final CourseClient courseClient;
    private final SearchClient searchClient;
    private final CatalogueClient catalogueClient;
    private final CategoryCache categoryCache;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Override
    public void saveQuestion(QuestionFormDTO questionDTO) {
        // 1. 获取当前登录的用户id
        Long userId = UserContext.requireUser();
        if(jdbc.queryForObject("SELECT COUNT(*) FROM learning_lesson WHERE user_id=? AND course_id=? AND status<>3 AND (expire_time IS NULL OR expire_time>NOW())",Integer.class,userId,questionDTO.getCourseId())!=1)
            throw new com.tianji.common.exceptions.ForbiddenException("需要有效课程权益才能提问");
        // 2. 数据封装
        InteractionQuestion question = BeanUtils.copyBean(questionDTO, InteractionQuestion.class);
        question.setUserId(userId);
        question.setAnonymity(Boolean.TRUE.equals(questionDTO.getAnonymity()));
        // 3. 写入数据库
        save(question);
    }

    @Override
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query) {
        // 1. 参数校验, 课程id和小节id不能都为空
        Long courseId = query.getCourseId();
        Long sectionId = query.getSectionId();
        if(courseId == null && sectionId == null) {
            throw new BadRequestException("课程id和小节id不能都为空");
        }

        // 2. 分页查询
        Page<InteractionQuestion> page = lambdaQuery()
                .select(InteractionQuestion.class, info -> !info.getProperty().equals("description"))
                .eq(Boolean.TRUE.equals(query.getOnlyMine()), InteractionQuestion::getUserId, UserContext.getUser())
                .eq(courseId != null, InteractionQuestion::getCourseId, courseId)
                .eq(sectionId != null, InteractionQuestion::getSectionId, sectionId)
                .eq(InteractionQuestion::getHidden, false)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if(CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }

        // 3. 根据id查询提问者和最近一次回答的信息
        Set<Long> userIds = new HashSet<>();
        Set<Long> answerIds = new HashSet<>();
        // 3.1 得到问题当中的提问者id个最近一次回答的id
        for (InteractionQuestion q : records) {
            if(!q.getAnonymity()) { // 只查询非匿名的问题
                userIds.add(q.getUserId());
            }
            answerIds.add(q.getLatestAnswerId());
        }
        // 3.2 根据id查询最近一次的回答
        answerIds.remove(null);
        Map<Long, InteractionReply> replyMap = new HashMap<>(answerIds.size());
        if(CollUtils.isNotEmpty(answerIds)) {
            List<InteractionReply> replies = replyService.listByIds(answerIds);
            for (InteractionReply reply : replies) {
                replyMap.put(reply.getId(), reply);
                if(!reply.getAnonymity()) {
                    userIds.add(reply.getUserId());
                }
            }
        }
        // 3.3 根据id查询用户信息(提问者)
        userIds.remove(null);
        Map<Long, UserDTO> userMap = new HashMap<>(userIds.size());
        if(CollUtils.isNotEmpty(userIds)) {
            List<UserDTO> users = userClient.queryUserByIds(userIds);
            userMap = users.stream()
                    .collect(Collectors.toMap(UserDTO::getId, u -> u));
        }

        // 4. 封装VO
        List<QuestionVO> voList = new ArrayList<>(records.size());
        for (InteractionQuestion r : records) {
            // 4.1 将PO转为VO
            QuestionVO vo = BeanUtils.copyBean(r, QuestionVO.class);
            voList.add(vo);
            // 4.2 封装提问者信息
            if(!r.getAnonymity()) {
                UserDTO userDTO = userMap.get(r.getUserId());
                if(userDTO != null) {
                    vo.setUserName(userDTO.getName());
                    vo.setUserIcon(userDTO.getIcon());
                }
            }
            // 4.3 封装最近一次回答的信息
            InteractionReply reply = replyMap.get(r.getLatestAnswerId());
            if(reply != null) {
                vo.setLatestReplyContent(reply.getContent());
                if(!reply.getAnonymity()) {
                    UserDTO user = userMap.get(reply.getUserId());
                    if(user!=null) vo.setLatestReplyUser(user.getName());
                }
            }
        }

        return PageDTO.of(page, voList);
    }

    @Override
    public QuestionVO queryQuestionById(Long id) {
        // 1. 根据id查询数据
        InteractionQuestion question = getById(id);
        // 2. 数据校验
        if(question == null || question.getHidden()) {
            // 没有数据或者被隐藏了
            return null;
        }
        // 3. 查询提问者信息
        UserDTO user = null;
        if(!question.getAnonymity()) {
            user = userClient.queryUserById(question.getUserId());
        }
        // 4. 封装VO
        QuestionVO vo = BeanUtils.copyBean(question, QuestionVO.class);
        if(user != null) {
            vo.setUserName(user.getName());
            vo.setUserIcon(user.getIcon());
        }
        return vo;
    }

    @Override
    public PageDTO<QuestionAdminVO> queryQuestionPageAdmin(QuestionAdminPageQuery query) {
        // 1. 处理课程名称, 得到课程id
        List<Long> courseIds = null;
        if(StringUtils.isNotBlank(query.getCourseName())) {
            courseIds = searchClient.queryCoursesIdByName(query.getCourseName());
            if(CollUtils.isEmpty(courseIds)) {
                return PageDTO.empty(0L, 0L);
            }
        }

        // 2. 分页查询
        Integer status = query.getStatus();
        LocalDateTime begin = query.getBeginTime();
        LocalDateTime end = query.getEndTime();
        Page<InteractionQuestion> page = lambdaQuery()
                .in(courseIds != null, InteractionQuestion::getCourseId, courseIds)
                .eq(status != null, InteractionQuestion::getStatus, status)
                .gt(begin != null, InteractionQuestion::getCreateTime, begin)
                .lt(end != null, InteractionQuestion::getCreateTime, end)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if(CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }

        // 3. 准备VO需要的数据: 用户数据, 课程数据, 章节数据, 分类数据
        Set<Long> userIds = new HashSet<>();
        Set<Long> cIds = new HashSet<>();
        Set<Long> cataIds = new HashSet<>();
        // 3.1 获取各种数据的id集合
        for (InteractionQuestion q : records) {
            userIds.add(q.getUserId());
            cIds.add(q.getCourseId());
            cataIds.add(q.getChapterId());
            cataIds.add(q.getSectionId());
        }
        // 3.2.根据id查询用户
        List<UserDTO> users = userClient.queryUserByIds(userIds);
        Map<Long, UserDTO> userMap = new HashMap<>(users.size());
        if (CollUtils.isNotEmpty(users)) {
            userMap = users.stream().collect(Collectors.toMap(UserDTO::getId, u -> u));
        }
        // 3.3.根据id查询课程
        List<CourseSimpleInfoDTO> cInfos = courseClient.getSimpleInfoList(cIds);
        Map<Long, CourseSimpleInfoDTO> cInfoMap = new HashMap<>(cInfos.size());
        if (CollUtils.isNotEmpty(cInfos)) {
            cInfoMap = cInfos.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        }
        // 3.4.根据id查询章节
        List<CataSimpleInfoDTO> catas = catalogueClient.batchQueryCatalogue(cataIds);
        Map<Long, String> cataMap = new HashMap<>(catas.size());
        if (CollUtils.isNotEmpty(catas)) {
            cataMap = catas.stream()
                    .collect(Collectors.toMap(CataSimpleInfoDTO::getId, CataSimpleInfoDTO::getName));
        }

        // 4.封装VO
        List<QuestionAdminVO> voList = new ArrayList<>(records.size());
        for (InteractionQuestion q : records) {
            // 4.1.将PO转VO，属性拷贝
            QuestionAdminVO vo = BeanUtils.copyBean(q, QuestionAdminVO.class);
            voList.add(vo);
            // 4.2.用户信息
            UserDTO user = userMap.get(q.getUserId());
            if (user != null) {
                vo.setUserName(user.getName());
            }
            // 4.3.课程信息以及分类信息
            CourseSimpleInfoDTO cInfo = cInfoMap.get(q.getCourseId());
            if (cInfo != null) {
                vo.setCourseName(cInfo.getName());
                vo.setCategoryName(categoryCache.getCategoryNames(cInfo.getCategoryIds()));
            }
            // 4.4.章节信息
            vo.setChapterName(cataMap.getOrDefault(q.getChapterId(), ""));
            vo.setSectionName(cataMap.getOrDefault(q.getSectionId(), ""));
        }
        return PageDTO.of(page, voList);
    }

    @Override
    public void updateQuestion(Long id, QuestionFormDTO dto) {
        //1.校验
        //1.1.手动检验部分属性
        if (StringUtils.isBlank(dto.getTitle()) ||  //标题、互动问题描述、是否匿名提问
                StringUtils.isBlank(dto.getDescription()) || dto.getAnonymity() == null) {
            throw new BadRequestException("非法参数");
        }
        //1.2.校验id
        InteractionQuestion question = getById(id);
        if (question == null) {
            throw new BadRequestException("非法参数");
        }
        //1.3.校验用户(只能修改自己的互动问题)
        Long userId = UserContext.getUser();
        if (!userId.equals(question.getUserId())) {  //Long类型不能用==比较
            throw new BadRequestException("不能修改别人的互动问题");
        }

        //2.将dto转换为po
        question.setTitle(dto.getTitle());
        question.setDescription(dto.getDescription());
        question.setAnonymity(dto.getAnonymity());

        //3.修改互动问题数据
        updateById(question);
    }

    @Override
    @Transactional  //保证事务一致性
    public void deleteQuestionById(Long id) {
        //1.获取当前登录用户id
        Long userId = UserContext.getUser();

        //2.查询问题是否存在
        InteractionQuestion question = this.getById(id);
        if (question == null) {
            throw new BadRequestException("要删除的问题不存在");
        }

        //3.判断是否是当前用户提问的
        if (!question.getUserId().equals(userId)) {  //Long类型不能用==比较
            //4.如果不是则报错
            throw new BadRequestException("不能删除别人的问题");
        }

        //5.如果是则删除问题
        //表：interaction_question，条件：id(主键)
        //removeById是MybatisPlus提供的方法，专门根据主键字段进行删除
        this.removeById(id);

        //6.然后删除问题下的回答及评论
        //表：interaction_reply，条件：questionId(外键)
        //master分支调用的是replyMapper.delete，我这里调用的是replyService.remove
        //由于是根据外键questionId进行删除，所以不能用removeById，构建查询条件可以new QueryWrapper或者是用Wrappers
        replyService.remove(Wrappers.<InteractionReply>lambdaQuery().eq(InteractionReply::getQuestionId, id));
    }

    @Override
    public void hiddenQuestionAdmin(Long id, Boolean hidden) {
        InteractionQuestion question = this.getById(id);
        if(question == null) {
            throw new BadRequestException("要隐藏或显示的问题不存在");
        }
        question.setHidden(hidden);
        this.updateById(question);
    }

    @Override
    public QuestionAdminVO queryQuestionAdminVOById(Long id) {
        //1.校验
        if (id == null) {
            throw new BadRequestException("非法参数");
        }

        //2.查询互动问题表
        //表：interaction_question，条件：id(主键)
        InteractionQuestion question = this.getById(id);
        if (question == null) {
            throw new BadRequestException("根据id查询的问题不存在");
        }

        //3.转po为vo
        QuestionAdminVO vo = BeanUtils.copyBean(question, QuestionAdminVO.class);

        //4.远程调用用户服务，获取用户信息
        UserDTO userDTO = userClient.queryUserById(question.getUserId());
        if (userDTO != null) {
            vo.setUserName(userDTO.getName());  //用户名称
            vo.setUserIcon(userDTO.getIcon());  //用户头像
        }

        //5.远程调用课程服务，获取课程信息
        CourseFullInfoDTO cinfo = courseClient
                .getCourseInfoById(question.getCourseId(), false, true);
        if (cinfo != null) {
            vo.setCourseName(cinfo.getName());  //课程名称
            vo.setCategoryName(categoryCache.getCategoryNames(cinfo.getCategoryIds()));  //三级分类名称，拼接字段
            List<Long> teacherIds = cinfo.getTeacherIds();
            if (CollUtils.isEmpty(teacherIds)) {
                throw new BizIllegalException("教师不存在");
            }
            List<UserDTO> teachers = userClient.queryUserByIds(teacherIds);
            if(CollUtils.isNotEmpty(teachers)) {
                vo.setTeacherName(teachers.stream()  //教师名称
                        .map(UserDTO::getName).collect(Collectors.joining("/")));
            }
        }

        //6.远程调用课程服务，获取章节信息
        Set<Long> chapterAndSectionIds = new HashSet<>(); //章和节id集合
        chapterAndSectionIds.add(question.getChapterId());
        chapterAndSectionIds.add(question.getSectionId());
        List<CataSimpleInfoDTO> cataSimpleInfoDTOS = catalogueClient.batchQueryCatalogue(chapterAndSectionIds);
        if (CollUtils.isEmpty(cataSimpleInfoDTOS)) {
            throw new BizIllegalException("章节信息不存在");
        }
        Map<Long, String> cataInfoDTOMap = cataSimpleInfoDTOS.stream()
                .collect(Collectors.toMap(CataSimpleInfoDTO::getId, c -> c.getName()));
        vo.setChapterName(cataInfoDTOMap.get(question.getChapterId()));  //章名称
        vo.setSectionName(cataInfoDTOMap.get(question.getSectionId()));  //节名称

        //7.已查看该问题，修改status为已查看
        question.setStatus(QuestionStatus.CHECKED);
        this.updateById(question);

        //8.返回vo
        return vo;
    }
}
