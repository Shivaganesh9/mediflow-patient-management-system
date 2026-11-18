package com.mediflow.appointment.schedule;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Verifies a booking slot against the doctor's working hours and approved leave.
 *
 * <p>Implemented by a synchronous call to doctor-service so a booking can never be committed for a
 * slot the doctor does not work. Implementations must fail closed: when the schedule cannot be
 * checked the booking is rejected instead of being allowed through.</p>
 */
public interface DoctorScheduleValidator {

    /**
     * @throws com.mediflow.common.exception.ConflictException when the slot is outside working hours
     *         or overlaps approved leave
     * @throws com.mediflow.common.exception.ResourceNotFoundException when the doctor does not exist
     * @throws com.mediflow.common.exception.ServiceUnavailableException when the schedule cannot be
     *         checked
     */
    void validateSlot(UUID doctorId, LocalDateTime startTime, LocalDateTime endTime);
}
