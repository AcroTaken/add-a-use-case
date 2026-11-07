package team.projectpulse.student;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.activity.ActivityRepository;
import team.projectpulse.evaluation.PeerEvaluationRepository;
import team.projectpulse.system.StatusCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StudentDeactivationIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired StudentRepository students;
    @Autowired ActivityRepository activities;
    @Autowired PeerEvaluationRepository evaluations;
    @Value("${api.endpoint.base-url}") String baseUrl;

    private String adminToken;
    private String otherAdminToken;
    private String instructorToken;
    private String studentToken;

    private String login(String username) throws Exception {
        String payload = mvc.perform(post(baseUrl + "/users/login")
                        .with(httpBasic(username, "123456")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + new JSONObject(payload).getJSONObject("data").getString("token");
    }

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login("b.wei@abc.edu");
        otherAdminToken = login("t.cook@abc.edu");
        instructorToken = login("b.gates@abc.edu");
        studentToken = login("j.smith@abc.edu");
    }

    @Test
    void owningAdminDeactivatesStudentAndPreservesTeamAndPriorWork() throws Exception {
        Student target = students.findById(4).orElseThrow();
        Integer originalTeamId = target.getTeam().getTeamId();
        Integer originalSectionId = target.getSection().getSectionId();
        long activitiesBefore = activities.count();
        long evaluationsBefore = evaluations.count();

        mvc.perform(patch(baseUrl + "/students/4/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(StatusCode.SUCCESS))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.teamId").value(originalTeamId));

        Student disabled = students.findById(4).orElseThrow();
        assertThat(disabled.isEnabled()).isFalse();
        assertThat(disabled.getTeam().getTeamId()).isEqualTo(originalTeamId);
        assertThat(disabled.getSection().getSectionId()).isEqualTo(originalSectionId);
        assertThat(activities.count()).isEqualTo(activitiesBefore);
        assertThat(evaluations.count()).isEqualTo(evaluationsBefore);
    }

    @Test
    void instructorAssignedToStudentsSectionMayDeactivate() throws Exception {
        mvc.perform(patch(baseUrl + "/students/4/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));
        assertThat(students.findById(4).orElseThrow().isEnabled()).isFalse();
    }

    @Test
    void repeatDeactivationReturns409AndDoesNotChangeHistory() throws Exception {
        mvc.perform(patch(baseUrl + "/students/4/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk());
        long savedEvaluations = evaluations.count();

        mvc.perform(patch(baseUrl + "/students/4/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(StatusCode.CONFLICT));

        assertThat(evaluations.count()).isEqualTo(savedEvaluations);
    }

    @Test
    void anAdminOfAnotherCourseAndAStudentCannotDeactivateTarget() throws Exception {
        mvc.perform(patch(baseUrl + "/students/4/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, otherAdminToken))
                .andExpect(status().isForbidden());
        mvc.perform(patch(baseUrl + "/students/4/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, studentToken))
                .andExpect(status().isForbidden());

        assertThat(students.findById(4).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void deactivationRejectsNewLoginAndOldJwtButNotUnrelatedActiveAccount() throws Exception {
        mvc.perform(patch(baseUrl + "/students/4/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk());

        mvc.perform(post(baseUrl + "/users/login")
                        .with(httpBasic("j.smith@abc.edu", "123456")))
                .andExpect(status().isUnauthorized());

        mvc.perform(get(baseUrl + "/students/4")
                        .header(HttpHeaders.AUTHORIZATION, studentToken))
                .andExpect(status().isUnauthorized());

        mvc.perform(get(baseUrl + "/students/4")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void oldJwtWithNoMutationIsStillValidForActiveStudent() throws Exception {
        mvc.perform(get(baseUrl + "/students/4")
                        .header(HttpHeaders.AUTHORIZATION, studentToken))
                .andExpect(status().isOk());
    }
}
