package team.projectpulse.evaluation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.projectpulse.section.Section;
import team.projectpulse.section.SectionRepository;
import team.projectpulse.student.Student;
import team.projectpulse.student.StudentRepository;
import team.projectpulse.system.exception.PeerEvaluationIllegalArgumentException;
import team.projectpulse.team.Team;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeactivatedStudentEvaluationTest {

    @Mock PeerEvaluationRepository evaluations;
    @Mock StudentRepository students;
    @Mock SectionRepository sections;

    private EvaluationService service;
    private Student evaluator;
    private Student inactive;
    private Section section;

    @BeforeEach
    void setup() {
        // 2023-08-13 is in W32; W31 is eligible for submission.
        Clock fixed = Clock.fixed(Instant.parse("2023-08-13T12:00:00Z"), ZoneId.of("UTC"));
        service = new EvaluationService(evaluations, students, sections, fixed);
        section = new Section();
        section.setSectionId(2);
        section.setSectionName("Course section");
        section.setActiveWeeks(List.of("2023-W31"));
        Team team = new Team("Team1", "Description", "https://example.org");
        team.setTeamId(1);

        evaluator = new Student("evaluator", "Eva", "Active", "eva@example.edu", "pw", true, "student");
        evaluator.setId(1);
        evaluator.setTeam(team);
        evaluator.setSection(section);

        inactive = new Student("inactive", "Ian", "Inactive", "ian@example.edu", "pw", false, "student");
        inactive.setId(2);
        inactive.setTeam(team);
        inactive.setSection(section);
    }

    @Test
    void newPeerEvaluationOfInactiveTeammateIsRejected() {
        PeerEvaluation proposed = new PeerEvaluation("2023-W31", evaluator, inactive, List.of(), "public", "private");

        assertThatThrownBy(() -> service.addPeerEvaluation(proposed))
                .isInstanceOf(PeerEvaluationIllegalArgumentException.class)
                .hasMessage("You cannot evaluate a deactivated student.");
        verify(evaluations, never()).save(any());
    }

    @Test
    void pastReportStillShowsInactiveStudentWhenSheHasRecordedEvaluations() {
        PeerEvaluation recorded = new PeerEvaluation("2023-W31", evaluator, inactive,
                List.of(), "Historical comment", "Private history");
        given(sections.findById(2)).willReturn(Optional.of(section));
        given(students.findBySectionSectionId(2)).willReturn(List.of(evaluator, inactive));
        given(evaluations.findByWeekAndEvaluateeId("2023-W31", 2)).willReturn(List.of(recorded));

        WeeklyPeerEvaluationReport report = service.generateWeeklyPeerEvaluationReportForSection(2, "2023-W31");

        assertThat(report.getPeerEvaluationAverages()).hasSize(2);
        PeerEvaluationAverage historical = report.getPeerEvaluationAverages().get(1);
        assertThat(historical.getStudentId()).isEqualTo(2);
        assertThat(historical.getPublicComments()).containsExactly("Historical comment");
    }

    @Test
    void inactiveStudentWithoutStoredEvaluationIsNotAZeroScoreRow() {
        given(sections.findById(2)).willReturn(Optional.of(section));
        given(students.findBySectionSectionId(2)).willReturn(List.of(evaluator, inactive));
        // Mockito's empty-list default matches: no evaluation for this week.

        WeeklyPeerEvaluationReport report = service.generateWeeklyPeerEvaluationReportForSection(2, "2023-W31");

        assertThat(report.getPeerEvaluationAverages()).hasSize(1);
        assertThat(report.getPeerEvaluationAverages().get(0).getStudentId()).isEqualTo(1);
    }

    @Test
    void previouslyCreatedEvaluationRemainsEditableDuringItsOriginalWindow() {
        PeerEvaluation recorded = new PeerEvaluation("2023-W31", evaluator, inactive,
                List.of(), "Before", "Private before");
        recorded.setPeerEvaluationId(55);
        PeerEvaluation edit = new PeerEvaluation("2023-W31", evaluator, inactive,
                List.of(), "After", "Private after");
        given(evaluations.findById(55)).willReturn(Optional.of(recorded));
        given(evaluations.save(recorded)).willReturn(recorded);

        PeerEvaluation saved = service.updatePeerEvaluation(55, edit);

        assertThat(saved.getPublicComment()).isEqualTo("After");
        assertThat(saved.getPrivateComment()).isEqualTo("Private after");
        verify(evaluations).save(recorded);
    }
}
