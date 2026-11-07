package team.projectpulse.security.authorizationmanagers;

import org.jspecify.annotations.Nullable;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import team.projectpulse.section.SectionSecurityService;
import team.projectpulse.student.StudentRepository;

import java.util.function.Supplier;

/**
 * UC-STU-deactivate-student: only the owner of the student's course or an
 * instructor assigned to the student's section can change her account state.
 * An instructor's role alone is never sufficient; unknown IDs fail closed.
 */
@Component
public class StudentLifecycleAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final StudentRepository studentRepository;
    private final SectionSecurityService sectionSecurityService;

    public StudentLifecycleAuthorizationManager(StudentRepository studentRepository,
                                                SectionSecurityService sectionSecurityService) {
        this.studentRepository = studentRepository;
        this.sectionSecurityService = sectionSecurityService;
    }

    @Override
    public @Nullable AuthorizationResult authorize(Supplier<? extends @Nullable Authentication> authentication,
                                                    RequestAuthorizationContext context) {
        Authentication caller = authentication.get();
        Integer studentId = PathVariables.readId(context, "studentId");
        boolean canManage = caller != null && caller.isAuthenticated() && studentId != null &&
                caller.getAuthorities().stream().anyMatch(a ->
                        a.getAuthority().equals("ROLE_instructor") || a.getAuthority().equals("ROLE_admin")) &&
                this.studentRepository.findById(studentId).map(student -> {
                    Integer sectionId = student.getSection().getSectionId();
                    return this.sectionSecurityService.isSectionOwner(sectionId) ||
                            this.sectionSecurityService.isSectionInstructor(sectionId);
                }).orElse(false);
        return new AuthorizationDecision(canManage);
    }
}
