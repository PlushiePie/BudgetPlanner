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

    //методы для аналитики
    fun getAverageSpentPerDay(): Double
    {
        if (_transactions.isEmpty()) return 0.0
        val firstDate = _transactions.minByOrNull { it.date }?.date ?: return 0.0
        val lastDate = _transactions.maxByOrNull { it.date }?.date ?: return 0.0
        val days = ChronoUnit.DAYS.between(firstDate, lastDate).toDouble() + 1
        return if (days > 0) getTotalSpent() / days else 0.0
    }

    fun getAverageSpentPerWeek(): Double = getAverageSpentPerDay() * 7
    fun getAverageSpentPerMonth(): Double = getAverageSpentPerDay() * 30.44

    fun getMostExpensiveCategory(): Pair<String, Double>?
    {
        if (_categories.isEmpty()) return null
        return _categories.maxByOrNull { it.spent }?.let { it.name to it.spent }
    }

    fun getCategoryPercentage(categoryName: String): Double
    {
        val total = getTotalSpent()
        if (total == 0.0) return 0.0
        val category = _categories.find { it.name == categoryName }
        return (category?.spent ?: 0.0) / total * 100
    }

    fun getMonthEndForecast(): Double
    {
        val today = LocalDate.now()
        val daysLeftInMonth = ChronoUnit.DAYS.between(today, today.withDayOfMonth(today.lengthOfMonth())).toDouble() + 1
        val avgPerDay = getAverageSpentPerDay()
        val projectedSpending = avgPerDay * daysLeftInMonth
        val remainingBudget = getTotalBudget() - getTotalSpent()
        return remainingBudget - projectedSpending
    }

    private fun saveToFile()
    {
        try {
            val data = BudgetSaveData(_categories, _transactions)
            ObjectOutputStream(FileOutputStream(SAVE_FILE)).use { it.writeObject(data) }
        } catch (e: Exception)
        {
            println("Ошибка сохранения: ${e.message}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadFromFile()
    {
        val file = File(SAVE_FILE)
        if (!file.exists()) return

        try {
            ObjectInputStream(FileInputStream(file)).use { input ->
                val data = input.readObject() as BudgetSaveData
                _categories.clear()
                _categories.addAll(data.categories)
                _transactions.clear()
                _transactions.addAll(data.transactions)
            }
        } catch (e: Exception)
        {
            println("Ошибка загрузки: ${e.message}")
        }
    }
}

data class BudgetSaveData(
    val categories: List<Category>,
    val transactions: List<Transaction>
) : Serializable

class BudgetApp : JFrame()
{
    private val manager = BudgetManager()
    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    private val categoryPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = EmptyBorder(10, 10, 10, 10)
    }

    private val recentListModel = DefaultListModel<String>()
    private val recentList = JList(recentListModel).apply {
        font = Font("Monospaced", Font.PLAIN, 12)
        fixedCellHeight = 35
    }

    private val pieChartPlaceholder = JLabel(
        "[ЗАГЛУШКА] Здесь будет круговая диаграмма\nРазмер сегментов = потрачено / бюджет",
        SwingConstants.CENTER
    ).apply {
        border = BorderFactory.createDashedBorder(Color.GRAY)
        background = Color(240, 240, 240)
        isOpaque = true
        preferredSize = Dimension(400, 120)
    }

    init
    {
        title = "Планировщик трат — Бюджетирование"
        defaultCloseOperation = EXIT_ON_CLOSE
        setSize(650, 750)
        setLocationRelativeTo(null)

        setupUI()
        refreshAll()
    }

    private fun setupUI()
    {
        val mainPanel = JPanel(BorderLayout())
        mainPanel.add(pieChartPlaceholder, BorderLayout.NORTH)

        val scrollCategories = JScrollPane(categoryPanel).apply {
            border = BorderFactory.createTitledBorder("Категории бюджета")
        }
        mainPanel.add(scrollCategories, BorderLayout.CENTER)

        val recentPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("🕒 Последние траты (правый клик - удалить/отметить)")
            add(JScrollPane(recentList), BorderLayout.CENTER)
            preferredSize = Dimension(400, 200)
        }

        recentList.addMouseListener(object : java.awt.event.MouseAdapter()
        {
            override fun mouseClicked(e: java.awt.event.MouseEvent)
            {
                if (e.button == java.awt.event.MouseEvent.BUTTON3)
                {
                    val index = recentList.locationToIndex(e.point)
                    if (index != -1)
                    {
                        showTransactionContextMenu(index, e.x, e.y)
                    }
                }
            }
        })

        mainPanel.add(recentPanel, BorderLayout.SOUTH)

        // Меню
        val menuBar = JMenuBar()
        val actionsMenu = JMenu("📋 Действия")

        val addItem = JMenuItem("➕ Добавить трату")
        addItem.addActionListener { showAddDialog() }

        val viewAllItem = JMenuItem("📜 Просмотреть все траты")
        viewAllItem.addActionListener { showAllTransactionsDialog() }

        val editBudgetItem = JMenuItem("✏️ Редактировать бюджеты категорий")
        editBudgetItem.addActionListener { showEditBudgetDialog() }

        val setMonthlyBudgetItem = JMenuItem("💰 Установить бюджет на месяц")
        setMonthlyBudgetItem.addActionListener { showSetMonthlyBudgetDialog() }

        val analyticsItem = JMenuItem("📈 Прогнозы и аналитика")
        analyticsItem.addActionListener { showAnalyticsDialog() }

        val exitItem = JMenuItem("🚪 Выход")
        exitItem.addActionListener { dispose() }

        actionsMenu.add(addItem)
        actionsMenu.add(viewAllItem)
        actionsMenu.add(editBudgetItem)
        actionsMenu.add(setMonthlyBudgetItem)
        actionsMenu.addSeparator()
        actionsMenu.add(analyticsItem)
        actionsMenu.addSeparator()
        actionsMenu.add(exitItem)

        val helpMenu = JMenu("❓ Справка")
        val aboutItem = JMenuItem("О программе")
        aboutItem.addActionListener {
            JOptionPane.showMessageDialog(this,
                "Планировщик трат\nВерсия 2.0\n\nФункции:\n- Добавление трат\n- Просмотр всех трат\n- Удаление трат\n- Отметка выполненных\n- Установка бюджета на месяц\n- Прогнозы и аналитика\n- Автосохранение",
                "О программе", JOptionPane.INFORMATION_MESSAGE)
        }
        helpMenu.add(aboutItem)

        menuBar.add(actionsMenu)
        menuBar.add(helpMenu)
        setJMenuBar(menuBar)

        add(mainPanel)
    }