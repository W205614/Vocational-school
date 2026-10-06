package com.tianji.media.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.media.domain.dto.MediaDTO;
import com.tianji.media.domain.dto.MediaUploadResultDTO;
import com.tianji.media.domain.query.MediaQuery;
import com.tianji.media.domain.vo.MediaVO;
import com.tianji.media.domain.vo.VideoPlayVO;
import com.tianji.media.service.IMediaService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 媒资表，主要是视频文件 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2022-06-30
 */
@RestController
@RequestMapping("/medias")
@Tag(name = "媒资管理相关接口")
@RequiredArgsConstructor
public class MediaController {

    private final IMediaService mediaService;
    private final com.tianji.common.autoconfigure.reliability.OperationStore operations;
    private final com.tianji.api.client.course.CourseClient courses;
    private org.springframework.http.ResponseEntity<?> delete(java.util.List<Long> ids,String key){
        com.tianji.common.utils.UserContext.requireAdmin();
        if(ids==null || ids.isEmpty() || ids.size()>100)throw new com.tianji.common.exceptions.BadRequestException("每次最多删除 100 项");
        var quotes=courses.mediaUserInfo(ids);if(quotes==null)throw new com.tianji.common.exceptions.CommonException("媒资引用核对未完成，请重试");
        if(quotes.stream().anyMatch(q->q.getQuoteNum()>0))throw new com.tianji.common.exceptions.BadRequestException("仍被课程引用的媒资不能删除");
        return org.springframework.http.ResponseEntity.accepted().body(operations.submit(com.tianji.common.utils.UserContext.requireUser(),"RESOURCE_DELETE",key,
          new com.tianji.media.service.impl.StorageCleanupService.Request("MEDIA",ids,com.tianji.common.utils.UserContext.getRole())));
    }

    @Operation(summary = "分页搜索已上传媒资信息")
    @GetMapping
    public PageDTO<MediaVO> queryMediaPage(MediaQuery query){
        return mediaService.queryMediaPage(query);
    }

    @Operation(summary = "上传视频后保存媒资信息")
    @PostMapping
    public MediaDTO saveMedia(@RequestBody MediaUploadResultDTO result) {
        return mediaService.save(result);
    }

    @Operation(summary = "获取上传视频的授权签名")
    @GetMapping("/signature/upload")
    public String getUploadSignature(){
        return mediaService.getUploadSignature();
    }

    @Operation(summary = "获取播放视频的授权签名")
    @GetMapping("/signature/play")
    public VideoPlayVO getPlaySignature(
            @Parameter(description = "小节id", example = "1", required = true) @RequestParam("sectionId") Long sectionId){
        return mediaService.getPlaySignatureBySectionId(sectionId);
    }

    @Operation(summary = "管理端获取预览视频的授权签名")
    @GetMapping("/signature/preview")
    public VideoPlayVO getPreviewSignature(
            @Parameter(description = "媒资id", example = "1", required = true) @RequestParam("mediaId") Long mediaId){
        return mediaService.getPlaySignatureByMediaId(mediaId);
    }

    @Operation(summary = "删除媒资视频")
    @DeleteMapping("{mediaId}")
    public org.springframework.http.ResponseEntity<?> deleteMedia(
            @Parameter(description = "媒资id", example = "1", required = true) @PathVariable("mediaId") Long mediaId,@RequestHeader("Idempotency-Key") String key){
        return delete(java.util.List.of(mediaId),key);
    }

    @Operation(summary = "批量删除媒资视频")
    @DeleteMapping
    public org.springframework.http.ResponseEntity<?> deleteMedias(
            @Parameter(description = "媒资id集合，例如1,2,3", required = true) @RequestParam("ids") List<Long> mediaIds,@RequestHeader("Idempotency-Key") String key){
        return delete(mediaIds,key);
    }
}
