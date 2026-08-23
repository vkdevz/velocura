package com.velocura.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "patients")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "user")
@EqualsAndHashCode(exclude = "user")
public class Patient {

    @Id
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    @NotNull(message = "Associated user is required")
    private User user;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    private String gender;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "blood_group")
    private String bloodGroup;

    private String address;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = com.velocura.security.crypto.EncryptedStringConverter.class)
    private String allergies;

    @Column(name = "medical_history_timeline", columnDefinition = "TEXT")
    @Convert(converter = com.velocura.security.crypto.EncryptedStringConverter.class)
    private String medicalHistoryTimeline;
}
