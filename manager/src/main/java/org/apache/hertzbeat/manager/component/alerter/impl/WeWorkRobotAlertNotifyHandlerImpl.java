/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hertzbeat.manager.component.alerter.impl;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.hertzbeat.common.entity.alerter.Alert;
import org.apache.hertzbeat.common.entity.manager.NoticeReceiver;
import org.apache.hertzbeat.common.entity.manager.NoticeTemplate;
import org.apache.hertzbeat.manager.support.exception.AlertNoticeException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Send alarm information through enterprise WeChat
 */
@Component
@RequiredArgsConstructor
@Slf4j
final class WeWorkRobotAlertNotifyHandlerImpl extends AbstractAlertNotifyHandlerImpl {

    @Override
    public void send(NoticeReceiver receiver, NoticeTemplate noticeTemplate, Alert alert) {
        try {
            WeWorkWebHookDto weWorkWebHookDTO = new WeWorkWebHookDto();
            WeWorkWebHookDto.MarkdownDTO markdownDTO = new WeWorkWebHookDto.MarkdownDTO();
            markdownDTO.setContent(renderContent(noticeTemplate, alert));
            weWorkWebHookDTO.setMarkdown(markdownDTO);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<WeWorkWebHookDto> httpEntity = new HttpEntity<>(weWorkWebHookDTO, headers);
            String webHookUrl = alerterProperties.getWeWorkWebhookUrl() + receiver.getWechatId();
            ResponseEntity<CommonRobotNotifyResp> entity = restTemplate.postForEntity(webHookUrl, httpEntity, CommonRobotNotifyResp.class);
            if (entity.getStatusCode() == HttpStatus.OK) {
                assert entity.getBody() != null;
                if (entity.getBody().getErrCode() == 0) {
                    log.debug("Send WeWork webHook: {} Success", webHookUrl);
                    WeWorkWebHookDto weWorkWebHookTextDto = checkNeedAtNominator(receiver, alert);
                    if (!Objects.isNull(weWorkWebHookTextDto)) {
                        HttpEntity<WeWorkWebHookDto> httpEntityText = new HttpEntity<>(weWorkWebHookTextDto, headers);
                        restTemplate.postForEntity(webHookUrl, httpEntityText, CommonRobotNotifyResp.class);
                    }

                } else {
                    log.warn("Send WeWork webHook: {} Failed: {}", webHookUrl, entity.getBody().getErrMsg());
                    throw new AlertNoticeException(entity.getBody().getErrMsg());
                }
            } else {
                log.warn("Send WeWork webHook: {} Failed: {}", webHookUrl, entity.getBody());
                throw new AlertNoticeException("Http StatusCode " + entity.getStatusCode());
            }
        } catch (Exception e) {
            throw new AlertNoticeException("[WeWork Notify Error] " + e.getMessage());
        }
    }

    private WeWorkWebHookDto checkNeedAtNominator(NoticeReceiver receiver, Alert alert) {
        if (StringUtils.isBlank(receiver.getPhone()) && StringUtils.isBlank(receiver.getTgUserId())) {
            return null;
        }
        WeWorkWebHookDto weWorkWebHookTextDto = new WeWorkWebHookDto();
        weWorkWebHookTextDto.setMsgtype(WeWorkWebHookDto.TEXT);
        WeWorkWebHookDto.TextDTO textDto = new WeWorkWebHookDto.TextDTO();
        String alertMessage = String.format("警告对象：%s\n详情：%s", alert.getTarget(), alert.getContent());
        textDto.setContent(alertMessage);
        if (StringUtils.isNotBlank(receiver.getPhone())) {
            textDto.setMentioned_mobile_list(analysisArgToList(receiver.getPhone()));
            weWorkWebHookTextDto.setText(textDto);
        }
        if (StringUtils.isNotBlank(receiver.getTgUserId())) {
            textDto.setMentioned_list(analysisArgToList(receiver.getTgUserId()));
            weWorkWebHookTextDto.setText(textDto);
        }
        return weWorkWebHookTextDto;

    }
    private List<String> analysisArgToList(String arg) {
        if (StringUtils.isBlank(arg)) {
            return Collections.emptyList();
        }
        //english symbol
        return Arrays.asList(arg.split("\\s*,\\s*"));
    }

    @Override
    public byte type() {
        return 4;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    private static class WeWorkWebHookDto {

        public static final String WEBHOOK_URL = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=";

        /**
         * markdown format
         */
        private static final String MARKDOWN = "markdown";

        /**
         * text format
         */
        private static final String TEXT = "text";

        /**
         * message type
         */
        @Builder.Default
        private String msgtype = MARKDOWN;

        /**
         * markdown message
         */
        private MarkdownDTO markdown;
        /**
         * text message
         */
        private TextDTO text;

        @Data
        private static class MarkdownDTO {

            /**
             * message content
             */
            private String content;
        }
        @Data
        private static class TextDTO {

            /**
             * message content
             */
            private String content;
            /**
             * @ userId
             */
            private List<String> mentioned_list;
            /**
             * @ phone
             */
            private List<String> mentioned_mobile_list;
        }

    }
}
