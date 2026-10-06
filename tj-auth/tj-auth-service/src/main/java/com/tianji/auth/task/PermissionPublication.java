package com.tianji.auth.task;
import com.tianji.auth.service.IPrivilegeService;
import com.tianji.auth.util.PrivilegeCache;
import com.tianji.common.autoconfigure.reliability.InboxStore;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
/** Snapshot writers serialize with database permission mutations; startup rebuilds committed state. */
@Component @RequiredArgsConstructor
public class PermissionPublication {
 private final IPrivilegeService privileges; private final PrivilegeCache cache;
 private final TransactionTemplate tx; private final InboxStore inbox;
 public void rebuild(){tx.executeWithoutResult(s->{cache.lockMutation();cache.publishCommittedSnapshot(privileges.listPrivilegeRoles());});}
 @RabbitListener(bindings=@QueueBinding(value=@Queue(name="auth.permissions.publication.queue",durable="true"),exchange=@Exchange(name="auth.permissions.exchange",type="topic"),key="auth.permissions.changed"))
 public void changed(Message message){inbox.once("auth.permissions",message.getMessageProperties().getMessageId(),()->{cache.lockMutation();cache.publishCommittedSnapshot(privileges.listPrivilegeRoles());});}
}
