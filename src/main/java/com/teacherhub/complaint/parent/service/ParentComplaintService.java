package com.teacherhub.complaint.parent.service;

import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.complaint.access.ComplaintAccessPolicy;
import com.teacherhub.complaint.parent.dto.complaint.ComplaintCreateRequest;
import com.teacherhub.complaint.parent.dto.complaint.ComplaintDraftUpdateRequest;
import com.teacherhub.complaint.parent.dto.complaint.ComplaintRecipientResponse;
import com.teacherhub.complaint.parent.dto.complaint.ComplaintResponse;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.complaint.entity.ComplaintEvent;
import com.teacherhub.risk.service.RiskAnalysisService;
import com.teacherhub.school.entity.SchoolClass;
import com.teacherhub.school.entity.StudentClass;
import com.teacherhub.school.repository.StudentClassRepository;
import com.teacherhub.user.entity.Parent;
import com.teacherhub.user.entity.Student;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.repository.ParentRepository;
import com.teacherhub.user.repository.ParentStudentRepository;
import com.teacherhub.user.entity.Teacher;
import com.teacherhub.user.repository.StudentRepository;
import com.teacherhub.user.repository.TeacherRepository;
import com.teacherhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import com.teacherhub.complaint.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParentComplaintService {

    private final UserRepository userRepository;
    private final ComplaintRepository complaintRepository;
    private final RiskAnalysisService riskAnalysisService;
    private final ComplaintEventRepository eventRepository;
    private final ParentRepository parentRepository;
    private final StudentRepository studentRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final StudentClassRepository studentClassRepository;
    private final TeacherRepository teacherRepository;
    private final ComplaintAccessPolicy complaintAccessPolicy;


    // 민원 초안 작성
    @Transactional
    public ComplaintResponse createDraft(
            Long userId,
            ComplaintCreateRequest request
    ) {

        // 작성할 때는 studentId가 반드시 필요
        // 로그인한 User와 연결된 Parent 조회
        Parent parent = parentRepository.findByUserId(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "학부모 정보를 찾을 수 없습니다."
                        )
                );


        // 학생 조회
        Student student = studentRepository.findById(request.studentId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "학생 정보를 찾을 수 없습니다."
                        )
                );


        // 해당 학생이 로그인한 학부모의 자녀인지 확인
        boolean isMyChild =
                parentStudentRepository.existsByParentAndStudent(
                        parent,
                        student
                );

        if (!isMyChild) {
            throw new IllegalArgumentException(
                    "해당 학부모와 연결된 학생이 아닙니다."
            );
        }

        Teacher teacher = findAvailableTeacher(student, request.teacherId());


        // Complaint 생성
        Complaint complaint = new Complaint(
                parent,
                student,
                teacher,
                request.title().trim(),
                request.content()
        );

        complaintRepository.save(complaint);
        eventRepository.save(ComplaintEvent.statusChanged(
                complaint, parent.getUser(), null, ComplaintStatus.DRAFT));

        return ComplaintResponse.builder()
                .complaintId(complaint.getId())
                .status(complaint.getStatus())
                .waitingFor(complaint.getWaitingFor())
                .build();
    }


    public ComplaintResponse findStatus(Long userId, Long complaintId) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ComplaintNotFoundException("민원을 찾을 수 없습니다."));

        User user = findUser(userId);

        complaintAccessPolicy.validateParentOwner(user, complaint);
        return ComplaintResponse.builder()
                .complaintId(complaint.getId())
                .status(complaint.getStatus())
                .waitingFor(complaint.getWaitingFor())
                .build();
    }

    // 민원 내용 수정
    @Transactional
    public void updateDraft(
            Long userId,
            Long complaintId,
            ComplaintDraftUpdateRequest request
    ) {

        Complaint complaint = complaintRepository.findForUpdate(complaintId)
                .orElseThrow(() ->
                        new ComplaintNotFoundException("민원을 찾을 수 없습니다.")
                );


        // 본인의 민원인지 확인
        User user = findUser(userId);

        complaintAccessPolicy.validateParentOwner(user, complaint);

        // DRAFT 상태만 수정 가능
        if (complaint.getStatus() != ComplaintStatus.DRAFT) {
            throw new ComplaintNotDraftException("작성 중인 민원만 수정할 수 있습니다.");
        }

        complaint.updateDraft(request.content());
    }


    //4. 민원 최종 제출
    @Transactional
    public void submitComplaint(
            Long userId,
            Long complaintId
    ) {

        Complaint complaint = complaintRepository.findForUpdate(complaintId)
                .orElseThrow(() ->
                        new ComplaintNotFoundException("민원을 찾을 수 없습니다."
                        )
                );


        // 본인의 민원인지 확인
        User user = findUser(userId);
        complaintAccessPolicy.validateParentOwner(user, complaint);


        // 이미 제출한 민원인지 검사
        if (complaint.getStatus() != ComplaintStatus.DRAFT) {
            throw new ComplaintNotDraftException("이미 제출된 민원입니다.");
        }


        // 전송 요청의 최종 내용을 분석한 후에만 교사를 지정하고 제출을 확정
        riskAnalysisService.analyzeForSubmission(userId, complaintId);

        // 최종 제출
        complaint.submit();

        eventRepository.save(ComplaintEvent.statusChanged(
                complaint,
                complaint.getParent().getUser(),
                ComplaintStatus.DRAFT,
                ComplaintStatus.OPEN
        ));
        eventRepository.save(ComplaintEvent.messageSent(complaint.initialMessage()));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new UnauthorizedException("사용자를 찾을 수 없습니다.")
                );
    }

    public java.util.List<ComplaintRecipientResponse> findRecipients(Long userId, Long studentId) {
        Parent parent = parentRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("학부모 정보를 찾을 수 없습니다."));
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생 정보를 찾을 수 없습니다."));
        if (!parentStudentRepository.existsByParentAndStudent(parent, student)) {
            throw new ComplaintAccessDeniedException("해당 학부모와 연결된 학생이 아닙니다.");
        }

        SchoolClass schoolClass = latestSchoolClass(student);
        return schoolClass.availableTeachers().stream()
                .map(teacher -> ComplaintRecipientResponse.from(teacher, schoolClass.getHomeroomTeacher()))
                .sorted(java.util.Comparator
                        .comparing(ComplaintRecipientResponse::homeroomTeacher).reversed()
                        .thenComparing(ComplaintRecipientResponse::teacherName))
                .toList();
    }

    private Teacher findAvailableTeacher(Student student, Long teacherId) {
        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new InvalidComplaintRecipientException("문의 대상을 찾을 수 없습니다."));
        if (!latestSchoolClass(student).hasAssignedTeacher(teacher)) {
            throw new InvalidComplaintRecipientException("학생의 담당 교사에게만 문의를 보낼 수 있습니다.");
        }
        return teacher;
    }

    private SchoolClass latestSchoolClass(Student student) {
        return studentClassRepository.findTopByStudentOrderBySchoolClass_AcademicYearDesc(student)
                .map(StudentClass::getSchoolClass)
                .orElseThrow(() -> new IllegalArgumentException("학생의 학급 정보를 찾을 수 없습니다."));
    }


}
