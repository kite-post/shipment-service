package ua.kite.shipment.shipment;

import jakarta.persistence.Embeddable;

/** Габарити місця в міліметрах (6.1). */
@Embeddable
public class ParcelDimensions {

    private Integer lengthMm;
    private Integer widthMm;
    private Integer heightMm;

    protected ParcelDimensions() {}

    public ParcelDimensions(Integer lengthMm, Integer widthMm, Integer heightMm) {
        this.lengthMm = lengthMm;
        this.widthMm = widthMm;
        this.heightMm = heightMm;
    }

    public Integer getLengthMm() {
        return lengthMm;
    }

    public Integer getWidthMm() {
        return widthMm;
    }

    public Integer getHeightMm() {
        return heightMm;
    }
}
