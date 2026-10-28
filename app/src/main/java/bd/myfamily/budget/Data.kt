package bd.myfamily.budget

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tx")
data class Tx(@PrimaryKey(autoGenerate = true) val id: Long = 0, val type: String, val date: Long, val cat: String, val amount: Long, val note: String = "")

@Entity(tableName = "budget")
data class BudgetItem(@PrimaryKey val code: String, val name: String, val amount: Long, val sort: Int)

@Entity(tableName = "creditor")
data class Creditor(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val amount: Long?, val note: String = "")

@Entity(tableName = "debtpay")
data class DebtPay(@PrimaryKey(autoGenerate = true) val id: Long = 0, val date: Long, val creditorId: Long, val brother: String, val amount: Long, val note: String = "")

@Entity(tableName = "goal")
data class Goal(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val target: Long, val saved: Long, val cat: String? = null)

@Entity(tableName = "upcoming")
data class Upcoming(@PrimaryKey(autoGenerate = true) val id: Long = 0, val title: String, val amount: Long, val due: Long, val done: Boolean = false)

@Entity(tableName = "kv")
data class Kv(@PrimaryKey val k: String, val v: String)

@Dao
interface BudgetDao {
    @Query("SELECT * FROM tx ORDER BY date DESC, id DESC") fun txs(): Flow<List<Tx>>
    @Query("SELECT * FROM tx") suspend fun allTx(): List<Tx>
    @Query("SELECT COUNT(*) FROM tx WHERE type=:t AND date=:d AND cat=:c AND amount=:a AND note=:n AND id!=:ex")
    suspend fun dup(t: String, d: Long, c: String, a: Long, n: String, ex: Long): Int
    @Upsert suspend fun upTx(t: Tx): Long
    @Delete suspend fun delTx(t: Tx)

    @Query("SELECT * FROM budget ORDER BY sort, code") fun budget(): Flow<List<BudgetItem>>
    @Query("SELECT * FROM budget") suspend fun allBudget(): List<BudgetItem>
    @Upsert suspend fun upB(b: BudgetItem): Long
    @Delete suspend fun delB(b: BudgetItem)

    @Query("SELECT * FROM creditor") fun creditors(): Flow<List<Creditor>>
    @Query("SELECT * FROM creditor") suspend fun allCreditors(): List<Creditor>
    @Upsert suspend fun upC(c: Creditor): Long
    @Delete suspend fun delC(c: Creditor)

    @Query("SELECT * FROM debtpay ORDER BY date DESC, id DESC") fun pays(): Flow<List<DebtPay>>
    @Query("SELECT * FROM debtpay") suspend fun allPays(): List<DebtPay>
    @Upsert suspend fun upP(p: DebtPay): Long
    @Delete suspend fun delP(p: DebtPay)

    @Query("SELECT * FROM goal") fun goals(): Flow<List<Goal>>
    @Query("SELECT * FROM goal") suspend fun allGoals(): List<Goal>
    @Upsert suspend fun upG(g: Goal): Long
    @Delete suspend fun delG(g: Goal)

    @Query("SELECT * FROM upcoming ORDER BY due") fun ups(): Flow<List<Upcoming>>
    @Query("SELECT * FROM upcoming") suspend fun allUps(): List<Upcoming>
    @Upsert suspend fun upU(u: Upcoming): Long
    @Delete suspend fun delU(u: Upcoming)

    @Query("SELECT * FROM kv") fun kvs(): Flow<List<Kv>>
    @Query("SELECT * FROM kv") suspend fun allKv(): List<Kv>
    @Query("SELECT v FROM kv WHERE k=:k") suspend fun getKv(k: String): String?
    @Upsert suspend fun setKv(kv: Kv): Long
}

@Database(
    entities = [Tx::class, BudgetItem::class, Creditor::class, DebtPay::class, Goal::class, Upcoming::class, Kv::class],
    version = 1, exportSchema = false
)
abstract class Db : RoomDatabase() {
    abstract fun dao(): BudgetDao

    companion object {
        @Volatile private var I: Db? = null
        // কোনো destructive migration নেই — অ্যাপ আপডেটে ডেটা মুছবে না।
        fun get(c: Context): Db = I ?: synchronized(this) {
            I ?: Room.databaseBuilder(c.applicationContext, Db::class.java, "family_budget.db").build().also { I = it }
        }
    }
}
