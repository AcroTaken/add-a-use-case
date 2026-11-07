package team.projectpulse.security.authorizationmanagers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import team.projectpulse.section.Section;
import team.projectpulse.section.SectionSecurityService;
import team.projectpulse.student.Student;
import team.projectpulse.student.StudentRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class StudentLifecycleAuthorizationManagerTest {

    @Mock StudentRepository students;
    @Mock SectionSecurityService sections;
    @InjectMocks StudentLifecycleAuthorizationManager manager;

    private Authentication actor(String role) {
        return new UsernamePasswordAuthenticationToken(
                "caller", "none", List.of(new SimpleGrantedAuthority(role)));
    }

    private RequestAuthorizationContext context(String id) {
        return new RequestAuthorizationContext(new MockHttpServletRequest(),
                Map.of("studentId", id));
    }

    private Student studentInSection() {
        Student student = new Student("student", "A", "Student", "a@tcu.edu", "pass", true, "student");
        Section section = new Section();
        section.setSectionId(2);
        student.setSection(section);
        return student;
    }

    @Test
    void assignedSectionInstructorMayDeactivate() {
        given(students.findById(7)).willReturn(Optional.of(studentInSection()));
        given(sections.isSectionInstructor(2)).willReturn(true);

        assertThat(manager.authorize(() -> actor("ROLE_instructor"), context("7")).isGranted()).isTrue();
    }

    @Test
    void owningCourseAdminMayDeactivateWithoutSectionAssignment() {
        given(students.findById(7)).willReturn(Optional.of(studentInSection()));
        given(sections.isSectionOwner(2)).willReturn(true);

        assertThat(manager.authorize(() -> actor("ROLE_admin"), context("7")).isGranted()).isTrue();
    }

    @Test
    void unrelatedInstructorIsDenied() {
        given(students.findById(7)).willReturn(Optional.of(studentInSection()));

        assertThat(manager.authorize(() -> actor("ROLE_instructor"), context("7")).isGranted()).isFalse();
    }

    @Test
    void aStudentCannotDeactivateAnyoneEvenInOwnSection() {
        assertThat(manager.authorize(() -> actor("ROLE_student"), context("7")).isGranted()).isFalse();
        verifyNoInteractions(students, sections);
    }

    @Test
    void nonResolvableTargetIsDeniedWithoutRevealingIt() {
        given(students.findById(777)).willReturn(Optional.empty());

        assertThat(manager.authorize(() -> actor("ROLE_admin"), context("777")).isGranted()).isFalse();
    }

    @Test
    void malformedTargetIdIsDeniedBeforeQuery() {
        assertThat(manager.authorize(() -> actor("ROLE_admin"), context("not-a-number")).isGranted()).isFalse();
        verifyNoInteractions(students, sections);
    }
}
