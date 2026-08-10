package com.velocura.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "vitals")
@Getter
@Setter
@ToString(exclude = "patient")
@EqualsAndHashCode(exclude = "patient")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vitals {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    private Integer systolic;
    private Integer diastolic;
    private Integer heartRate;
    private Integer bloodSugar;

    @Column(name = "recorded_at")
    private LocalDateTime recordedAt;
}
