package cards.project.mongo.entity

import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.stereotype.Repository
import tech.wanion.encryptable.mongo.*
import tech.wanion.encryptable.mongo.PartOf

/**
 * Order entity that uses TRUE polymorphic payment field
 */
@Document(collection = "test_orders")
class TestOrder : Encryptable<TestOrder>() {
    @HKDFId
    override var id: CID? = null

    @Encrypt
    var orderNumber: String? = null

    @Encrypt
    var customerName: String? = null

    @Encrypt
    var totalAmount: Double? = null

    /**
     * TRUE POLYMORPHIC FIELD: declared as Encryptable<*>,
     * can hold ANY Encryptable implementation:
     * - TestCreditCardPayment
     * - TestPixPayment
     * - TestBankTransferPayment
     * - Or ANY other Encryptable class
     *
     * but on this test, we're going to make it TestPayment, which is the base class
     *
     * This demonstrates version 1.0.7's polymorphism feature where the field type
     * differs from the actual instance type stored.
     */
    @PartOf
    var payment: TestPayment<*>? = null

    var status: String? = null
}

@Repository
interface TestOrderRepository : EncryptableMongoRepository<TestOrder>
