package com.tianji.api.client.privacy;

import org.springframework.cloud.openfeign.FeignClient;

public final class PrivacyClient {
    private PrivacyClient() {}

    @FeignClient(value = "auth-service", contextId = "privacy-auth")
    public interface Auth extends PrivacyDomainClient {
        default String domain() {
            return "auth";
        }
    }

    @FeignClient(value = "user-service", contextId = "privacy-user")
    public interface User extends PrivacyDomainClient {
        default String domain() {
            return "user";
        }
    }

    @FeignClient(value = "trade-service", contextId = "privacy-trade")
    public interface Trade extends PrivacyDomainClient {
        default String domain() {
            return "trade";
        }
    }

    @FeignClient(value = "pay-service", contextId = "privacy-pay")
    public interface Pay extends PrivacyDomainClient {
        default String domain() {
            return "pay";
        }
    }

    @FeignClient(value = "promotion-service", contextId = "privacy-promotion")
    public interface Promotion extends PrivacyDomainClient {
        default String domain() {
            return "promotion";
        }
    }

    @FeignClient(value = "course-service", contextId = "privacy-course")
    public interface Course extends PrivacyDomainClient {
        default String domain() {
            return "course";
        }
    }

    @FeignClient(value = "learning-service", contextId = "privacy-learning")
    public interface Learning extends PrivacyDomainClient {
        default String domain() {
            return "learning";
        }
    }

    @FeignClient(value = "exam-service", contextId = "privacy-exam")
    public interface Exam extends PrivacyDomainClient {
        default String domain() {
            return "exam";
        }
    }

    @FeignClient(value = "remark-service", contextId = "privacy-remark")
    public interface Remark extends PrivacyDomainClient {
        default String domain() {
            return "remark";
        }
    }

    @FeignClient(value = "media-service", contextId = "privacy-media")
    public interface Media extends PrivacyDomainClient {
        default String domain() {
            return "media";
        }
    }

    @FeignClient(value = "search-service", contextId = "privacy-search")
    public interface Search extends PrivacyDomainClient {
        default String domain() {
            return "search";
        }
    }

    @FeignClient(value = "message-service", contextId = "privacy-message")
    public interface Message extends PrivacyDomainClient {
        default String domain() {
            return "message";
        }
    }

    @FeignClient(value = "data-service", contextId = "privacy-data")
    public interface Data extends PrivacyDomainClient {
        default String domain() {
            return "data";
        }
    }
}
