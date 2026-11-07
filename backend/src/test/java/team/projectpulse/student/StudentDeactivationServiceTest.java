package team.projectpulse.student;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import team.projectpulse.section.Section;
import team.projectpulse.section.SectionRepository;
import team.projectpulse.system.UserUtils;
import team.projectpulse.system.exception.ObjectNotFoundException;
import team.projectpulse.system.exception.StudentAlreadyDeactivatedException;
import team.projectpulse.team.Team;
import team.projectpulse.user.userinvitation.UserInvitationService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentDeactivationServiceTest {

    @Mock StudentRepository studentRepository;
    @Mock SectionRepository sectionRepository;
    @Mock UserInvitationService userInvitationService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock UserUtils userUtils;
    @InjectMocks StudentService studentService;

    @Test
    void deactivationRetainsSectionAndTeamWithoutDeletingRecords() {
        Student student = new Student("student", "A", "Student", "a@example.edu", "password", true, "student");
        Section section = mock(Section.class);
        Team team = mock(Team.class);
        student.setSection(section);
        student.setTeam(team);
        given(studentRepository.findById(7)).willReturn(Optional.of(student));
        given(studentRepository.save(student)).willReturn(student);

        Student disabled = studentService.deactivateStudent(7);

        assertThat(disabled.isEnabled()).isFalse();
        assertThat(disabled.getSection()).isSameAs(section);
        assertThat(disabled.getTeam()).isSameAs(team);
        verify(studentRepository).save(student);
        verify(studentRepository, never()).deleteById(7);
    }

    @Test
    void alreadyInactiveStudentReturnsConflictWithoutWriting() {
        Student inactive = new Student("student", "A", "Student", "a@example.edu", "password", false, "student");
        given(studentRepository.findById(7)).willReturn(Optional.of(inactive));

        assertThatThrownBy(() -> studentService.deactivateStudent(7))
                .isInstanceOf(StudentAlreadyDeactivatedException.class);
        verify(studentRepository, never()).save(any());
    }

    @Test
    void unknownStudentCannotBeDeactivated() {
        given(studentRepository.findById(777)).willReturn(Optional.empty());

        assertThatThrownBy(() -> studentService.deactivateStudent(777))
                .isInstanceOf(ObjectNotFoundException.class);
        verify(studentRepository, never()).save(any());
    }

    @Test
    void storageFailureDoesNotReportSuccessfulDeactivation() {
        Student student = new Student("student", "A", "Student", "a@example.edu", "password", true, "student");
        given(studentRepository.findById(7)).willReturn(Optional.of(student));
        given(studentRepository.save(student)).willThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> studentService.deactivateStudent(7))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
        // DB rollback is a transaction/integration property, not proven by this mock.
    }

    @Test
    void genericProfilePutCannotChangeAccountState() {
        Student original = new Student("student", "A", "Student", "a@example.edu", "password", true, "student");
        Student update = new Student("updated", "New", "Name", "updated@example.edu", "", false, "instructor");
        given(studentRepository.findById(7)).willReturn(Optional.of(original));
        given(studentRepository.save(original)).willReturn(original);

        Student result = studentService.updateStudent(7, update);

        assertThat(result.isEnabled()).isTrue();
        assertThat(result.getRoles()).isEqualTo("student");
        assertThat(result.getFirstName()).isEqualTo("New");
    }
}
