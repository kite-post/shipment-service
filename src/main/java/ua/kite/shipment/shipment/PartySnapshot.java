package ua.kite.shipment.shipment;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Відправник або отримувач у тому вигляді, в якому їх вписали в накладну.
 * Це знімок: профіль клієнта може змінитися, а накладна — ні.
 */
@Embeddable
public class PartySnapshot {

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    private String middleName;

    /** E.164. Назовні, у події, їде лише хеш (8.1, 6.2). */
    @Column(nullable = false)
    private String phone;

    private String email;

    protected PartySnapshot() {}

    public PartySnapshot(String firstName, String lastName, String middleName, String phone, String email) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.middleName = middleName;
        this.phone = phone;
        this.email = email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }
}
