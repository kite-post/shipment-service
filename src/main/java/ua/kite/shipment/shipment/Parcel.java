package ua.kite.shipment.shipment;

import java.util.UUID;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import ua.kite.shipment.common.UuidV7;

/** Місце відправлення: окрема коробка зі своїм штрих-кодом {@code KT…UA-01} (6.2, 10.1). */
@Entity
@Table(name = "parcel")
public class Parcel {

    @Id
    private UUID id;

    @Column(nullable = false)
    private int seq;

    @Column(nullable = false, unique = true)
    private String barcode;

    @Column(nullable = false)
    private long declaredWeightGrams;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "lengthMm", column = @Column(name = "declared_length_mm", nullable = false)),
        @AttributeOverride(name = "widthMm", column = @Column(name = "declared_width_mm", nullable = false)),
        @AttributeOverride(name = "heightMm", column = @Column(name = "declared_height_mm", nullable = false))
    })
    private ParcelDimensions declaredDimensions;

    /** Фактичні вага й габарити — після зважування на прийомі. */
    private Long actualWeightGrams;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "lengthMm", column = @Column(name = "actual_length_mm")),
        @AttributeOverride(name = "widthMm", column = @Column(name = "actual_width_mm")),
        @AttributeOverride(name = "heightMm", column = @Column(name = "actual_height_mm"))
    })
    private ParcelDimensions actualDimensions;

    protected Parcel() {}

    Parcel(int seq, String barcode, long declaredWeightGrams, ParcelDimensions declaredDimensions) {
        this.id = UuidV7.next();
        this.seq = seq;
        this.barcode = barcode;
        this.declaredWeightGrams = declaredWeightGrams;
        this.declaredDimensions = declaredDimensions;
    }

    public UUID getId() {
        return id;
    }

    public int getSeq() {
        return seq;
    }

    public String getBarcode() {
        return barcode;
    }

    public long getDeclaredWeightGrams() {
        return declaredWeightGrams;
    }

    public ParcelDimensions getDeclaredDimensions() {
        return declaredDimensions;
    }

    public Long getActualWeightGrams() {
        return actualWeightGrams;
    }

    public ParcelDimensions getActualDimensions() {
        return actualDimensions;
    }
}
