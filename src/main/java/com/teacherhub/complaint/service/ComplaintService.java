package com.teacherhub.complaint.service;

import com.teacherhub.complaint.dto.ComplaintRequest;
import com.teacherhub.complaint.dto.ComplaintResponse;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintStatus;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.school.entity.SchoolClass;
import com.teacherhub.school.entity.StudentClass;
import com.teacherhub.school.repository.StudentClassRepository;
import com.teacherhub.user.entity.Parent;
import com.teacherhub.user.entity.ParentStudent;
import com.teacherhub.user.entity.Student;
import com.teacherhub.user.repository.ParentRepository;
import com.teacherhub.user.repository.ParentStudentRepository;
import com.teacherhub.user.entity.Teacher;
import com.teacherhub.user.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final ParentRepository parentRepository;
    private final StudentRepository studentRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final StudentClassRepository studentClassRepository;


    /**
     * 1. 민원 초안 작성
     */
    @Transactional
    public ComplaintResponse createDraft(
            Long userId,
            ComplaintRequest request
    ) {

        // 작성할 때는 studentId가 반드시 필요
        if (request.getStudentId() == null) {
            throw new IllegalArgumentException(
                    "학생을 선택해주세요."
            );
        }


        // 로그인한 User와 연결된 Parent 조회
        Parent parent = parentRepository.findByUserId(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "학부모 정보를 찾을 수 없습니다."
                        )
                );


        // 학생 조회
        Student student = studentRepository.findById(request.getStudentId())
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


        // Complaint 생성
        Complaint complaint = Complaint.builder()
                .parent(parent)
                .student(student)
                .content(request.getContent())
                .build();


        complaintRepository.save(complaint);


        return ComplaintResponse.builder()
                .complaintId(complaint.getId())
                .status(complaint.getStatus())
                .build();
    }


    /**
     * 3. 민원 내용 수정
     */
    @Transactional
    public void updateDraft(
            Long userId,
            Long complaintId,
            ComplaintRequest request
    ) {

        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "민원을 찾을 수 없습니다."
                        )
                );


        // 본인의 민원인지 확인
        validateOwner(
                userId,
                complaint
        );


        // DRAFT 상태만 수정 가능
        if (complaint.getStatus() != ComplaintStatus.DRAFT) {
            throw new IllegalStateException(
                    "작성 중인 민원만 수정할 수 있습니다."
            );
        }


        // ComplaintRequest의 content만 사용
        complaint.updateContent(
                request.getContent()
        );
    }


    /**
     * 4. 민원 최종 제출
     */
    @Transactional
    public void submitComplaint(
            Long userId,
            Long complaintId,
            String idempotencyKey
    ) {

        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "민원을 찾을 수 없습니다."
                        )
                );


        // 본인의 민원인지 확인
        validateOwner(
                userId,
                complaint
        );


        // 이미 제출한 민원인지 검사
        if (complaint.getStatus() != ComplaintStatus.DRAFT) {
            throw new IllegalStateException(
                    "이미 제출된 민원입니다."
            );
        }


        // 중복 제출 방지
        if (complaintRepository.existsByIdempotencyKey(idempotencyKey)) {
            throw new IllegalStateException(
                    "이미 처리된 요청입니다."
            );
        }


        Student student = complaint.getStudent();


        // 학생의 가장 최근 학급 조회
        StudentClass studentClass =
                studentClassRepository
                        .findTopByStudentOrderBySchoolClass_AcademicYearDesc(student)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "학생의 학급 정보를 찾을 수 없습니다."
                                )
                        );


        SchoolClass schoolClass =
                studentClass.getSchoolClass();


        Teacher teacher =
                schoolClass.getHomeroomTeacher();


        if (teacher == null) {
            throw new IllegalStateException(
                    "담임교사가 등록되어 있지 않습니다."
            );
        }


        // 최종 제출
        complaint.submit(
                teacher,
                idempotencyKey
        );
    }


    /**
     * 민원 작성자 검사
     */
    private void validateOwner(
            Long userId,
            Complaint complaint
    ) {

        Long complaintUserId =
                complaint.getParent()
                        .getUser()
                        .getId();


        if (!complaintUserId.equals(userId)) {

            throw new IllegalArgumentException(
                    "해당 민원에 접근할 권한이 없습니다."
            );
        }
    }
}
