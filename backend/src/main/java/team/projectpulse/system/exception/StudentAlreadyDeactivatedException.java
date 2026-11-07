package team.projectpulse.system.exception;

/** UC-STU-deactivate-student extension 3a: refuse an already inactive account. */
public class StudentAlreadyDeactivatedException extends RuntimeException {
    public StudentAlreadyDeactivatedException() {
        super("The selected student is already deactivated.");
    }
}
