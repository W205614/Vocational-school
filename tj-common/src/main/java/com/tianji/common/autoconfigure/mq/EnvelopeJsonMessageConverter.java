package com.tianji.common.autoconfigure.mq;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConversionException;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
/** Uniform stored envelope; business listeners still receive their typed payload. */
public class EnvelopeJsonMessageConverter extends JacksonJsonMessageConverter {
    private final JsonMapper json;
    public EnvelopeJsonMessageConverter(JsonMapper json) {super(json);this.json=json;}
    @Override public Object fromMessage(Message message,Object hint) throws MessageConversionException {
        var tree=json.readTree(message.getBody());
        if(tree.has("eventId") && tree.has("schemaVersion") && tree.has("payload")) {
            String id=tree.get("eventId").asString();
            if(!id.equals(message.getMessageProperties().getMessageId()) || tree.get("schemaVersion").asInt()!=1)
                throw new MessageConversionException("Unsupported event envelope or inconsistent event ID");
            Message payload=new Message(json.writeValueAsBytes(tree.get("payload")),message.getMessageProperties());
            return super.fromMessage(payload,hint);
        }
        return super.fromMessage(message,hint);
    }
}
