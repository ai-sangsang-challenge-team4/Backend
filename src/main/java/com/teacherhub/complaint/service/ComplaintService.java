package com.teacherhub.complaint.service;

import com.teacherhub.complaint.dto.ComplaintRequest;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.parent.entity.Parent;
import com.teacherhub.parent.repository.ParentRepository;
import com.teacherhub.teacher.entity.Teacher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;


@Service
@RequiredArgsConstructor
// 최종 전송
public class ComplaintService {

    private final ComplaintRepository complaintRepository;

    private final ParentRepository parentRepository;
//    private final ParentStudentRepository parentStudentRepository;
//    private final StudentClassRepository studentClassRepository;


    @Transactional
    public Complaint submitComplaint(
            Long parentId,
            ComplaintRequest request,
            String idempotencyKey
    ) {

        // 1. 중복 전송 확인
        Optional<Complaint> existingComplaint =
                complaintRepository.findByIdempotencyKey(idempotencyKey);

        if (existingComplaint.isPresent()) {
            return existingComplaint.get();
        }


        // 2. 학부모 조회
        Parent parent = parentRepository.findById(parentId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "학부모 정보를 찾을 수 없습니다."
                        )
                );


//        // 3. 학부모와 연결된 학생 조회
//        ParentStudent parentStudent = parentStudentRepository.findByParent_Id(parentId)
//                        .orElseThrow(() ->
//                                new IllegalArgumentException(
//                                        "연결된 학생을 찾을 수 없습니다."
//                                )
//                        );
//
//        Student student = parentStudent.getStudent();
//
//
//        // 4. 학생의 현재 학급 조회
//        StudentClass studentClass = studentClassRepository
//                        .findTopByStudent_IdOrderBySchoolClass_AcademicYearDesc(
//                                student.getId()
//                        )
//                        .orElseThrow(() ->
//                                new IllegalArgumentException(
//                                        "학생의 학급 정보를 찾을 수 없습니다."
//                                )
//                        );
//
//        SchoolClass schoolClass = studentClass.getSchoolClass();
//
//
//        // 5. 담임교사 조회
//        Teacher teacher = schoolClass.getHomeroomTeacher();
//
//        if (teacher == null) {
//            throw new IllegalStateException(
//                    "담임교사가 등록되어 있지 않습니다."
//            );
//        }


        // 6. 학부모가 최종 선택한 문장
        String submittedContent = request.getContent();


        // 8. Complaint 생성
        Complaint complaint = new Complaint(
                parent,
                //student,
                //schoolClass,
                teacher,
                submittedContent,
                idempotencyKey
        );


        // 9. 저장
        return complaintRepository.save(complaint);
    }
}
