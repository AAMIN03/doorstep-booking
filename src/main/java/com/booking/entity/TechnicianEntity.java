package com.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.Set;
import com.booking.enums.Skill;

@Entity
@Table(name = "technicians")
public class TechnicianEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private ZoneEntity zone;

    @Column(name = "shift_start_minute", nullable = false)
    private int shiftStartMinute;

    @Column(name = "shift_end_minute", nullable = false)
    private int shiftEndMinute;

    @ElementCollection(fetch = FetchType.EAGER)
    @JoinTable(name = "technician_skills", joinColumns = @JoinColumn(name = "technician_id"))
    @Column(name = "skill")
    @Enumerated(EnumType.STRING)
    private Set<Skill> skills;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ZoneEntity getZone() {
        return zone;
    }

    public LocalTime getShiftStart() {
        return LocalTime.of(shiftStartMinute / 60, shiftStartMinute % 60);
    }

    public LocalTime getShiftEnd() {
        return LocalTime.of(shiftEndMinute / 60, shiftEndMinute % 60);
    }

    public Set<Skill> getSkills() {
        return skills;
    }
}
