package com.teacherhub.complaint.access;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.exception.ComplaintAccessDeniedException;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.enums.UserRole;
import org.springframework.stereotype.Component;

@Component
public class ComplaintAccessPolicy {

    public void validateParentOwner(User user, Complaint complaint) {
        if (user.getRole() != UserRole.PARENT || !isParentOwner(user, complaint)) {
            throw denied();
        }
    }

    public void validateCanRead(User user, Complaint complaint) {
        boolean allowed = switch (user.getRole()) {
            case PARENT -> isParentOwner(user, complaint);
            case TEACHER -> isAssignedTeacher(user, complaint);
            case ADMIN -> false;
        };

        if (!allowed) {
            throw denied();
        }
    }

    public void validateAssignedTeacher(User user, Complaint complaint) {
        if (user.getRole() != UserRole.TEACHER || !isAssignedTeacher(user, complaint)) {
            throw denied();
        }
    }

    private boolean isParentOwner(User user, Complaint complaint) {
        return complaint.getParent().getUser().getId().equals(user.getId());
    }

    private boolean isAssignedTeacher(User user, Complaint complaint) {
        return complaint.getTeacher() != null
                && complaint.getTeacher().getUser().getId().equals(user.getId());
    }

    private ComplaintAccessDeniedException denied() {
        return new ComplaintAccessDeniedException("해당 민원에 접근할 권한이 없습니다.");
    }
}
