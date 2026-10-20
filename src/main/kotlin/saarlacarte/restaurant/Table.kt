package saarlacarte.restaurant

import saarlacarte.enums.TableStatus
import saarlacarte.enums.TableType

/**
 A table in the front of house
 **/
class Table(private val id: Int, private val type: TableType, private val size: Int) {
    private var status: TableStatus = TableStatus.FREE

    /** id **/
    fun getId(): Int = id

    /** type **/
    fun getType(): TableType = type

    /** size **/
    fun getSize(): Int = size

    /** status **/
    fun getStatus(): TableStatus = status

    /** sets status **/
    fun setStatus(newStatus: TableStatus) {
        status = newStatus
    }
}
