-- Which staff are assigned to a session. Employees only ever see the sessions they're assigned to.
CREATE TABLE booking_assignment (
    booking_id BIGINT      NOT NULL REFERENCES booking (id) ON DELETE CASCADE,
    staff_name VARCHAR(60) NOT NULL REFERENCES admin_user (name) ON DELETE CASCADE,
    PRIMARY KEY (booking_id, staff_name)
);
