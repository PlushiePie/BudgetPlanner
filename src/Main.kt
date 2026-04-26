import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GridLayout
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.swing.*
import javax.swing.border.EmptyBorder

data class Category(
    val name: String,
    val budget: Double,
    var spent: Double,
    val icon: String
) : Serializable

data class Transaction(
    val amount: Double,
    val category: String,
    val comment: String,
    val date: LocalDate,
    var isCompleted: Boolean = false
) : Serializable

//менеджер бюджета
class BudgetManager : Serializable
{
    private companion object
    {
        private const val SAVE_FILE = "budget_data.ser"
    }

    private val _categories = mutableListOf<Category>()
    private val _transactions = mutableListOf<Transaction>()

    val categories: List<Category> get() = _categories
    val transactions: List<Transaction> get() = _transactions

    init
    {
        loadFromFile()
        if (_categories.isEmpty())
        {
            _categories.addAll(listOf(
                Category("🍔 Еда", 20000.0, 0.0, "🍔"),
                Category("🚗 Транспорт", 8000.0, 0.0, "🚗"),
                Category("🎬 Развлечения", 5000.0, 0.0, "🎬"),
                Category("🏠 Жильё", 30000.0, 0.0, "🏠"),
                Category("💊 Здоровье", 4000.0, 0.0, "💊")
            ))
        }
    }

    fun addTransaction(amount: Double, categoryName: String, comment: String, date: LocalDate)
    {
        if (amount <= 0) return
        _transactions.add(0, Transaction(amount, categoryName, comment, date, false))
        updateCategorySpent(categoryName, amount)
        saveToFile()
    }

    fun deleteTransaction(index: Int)
    {
        if (index in _transactions.indices)
        {
            val transaction = _transactions[index]
            updateCategorySpent(transaction.category, -transaction.amount)
            _transactions.removeAt(index)
            saveToFile()
        }
    }

    fun markTransactionCompleted(index: Int)
    {
        if (index in _transactions.indices)
        {
            _transactions[index] = _transactions[index].copy(isCompleted = true)
            saveToFile()
        }
    }

    fun updateCategorySpent(categoryName: String, amount: Double) {
        val index = _categories.indexOfFirst { it.name == categoryName }
        if (index != -1)
        {
            _categories[index] = _categories[index].copy(spent = _categories[index].spent + amount)
        }
        saveToFile()
    }

    fun updateBudget(categoryName: String, newBudget: Double)
    {
        val index = _categories.indexOfFirst { it.name == categoryName }
        if (index != -1 && newBudget > 0)
        {
            _categories[index] = _categories[index].copy(budget = newBudget)
            saveToFile()
        }
    }

    fun setMonthlyBudget(totalAmount: Double)
    {
        if (totalAmount <= 0) return
        val currentTotal = getTotalBudget()
        if (currentTotal == 0.0) return

        val remainingCategories = _categories.toMutableList()
        var remainingBudget = totalAmount

        for (i in _categories.indices)
        {
            val proportion = _categories[i].budget / currentTotal
            var newBudget = totalAmount * proportion

            // Для последней категории ставим остаток, чтобы избежать погрешности
            if (i == _categories.size - 1)
            {
                newBudget = remainingBudget
            }

            _categories[i] = _categories[i].copy(budget = newBudget)
            remainingBudget -= newBudget
        }
        saveToFile()
    }

    fun getTotalBudget(): Double = _categories.sumOf { it.budget }
    fun getTotalSpent(): Double = _categories.sumOf { it.spent }