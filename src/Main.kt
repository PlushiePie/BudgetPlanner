import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GridLayout
import java.awt.FlowLayout
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

    // умный ввод суммы (1.5к → 1500, 2т → 2000)
    private fun parseAmount(input: String): Double
    {
        val trimmed = input.trim().lowercase()
        return when {
            trimmed.endsWith("к") -> trimmed.dropLast(1).toDoubleOrNull()?.times(1000) ?: 0.0
            trimmed.endsWith("т") -> trimmed.dropLast(1).toDoubleOrNull()?.times(1000) ?: 0.0
            else -> trimmed.toDoubleOrNull() ?: 0.0
        }
    }

    // экспорт в CSV
    private fun exportToCSV()
    {
        if (manager.transactions.isEmpty())
        {
            JOptionPane.showMessageDialog(this, "Нет трат для экспорта", "Ошибка", JOptionPane.WARNING_MESSAGE)
            return
        }
        val fileName = "отчёт_${LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))}.csv"
        val file = File(fileName)

        java.io.PrintWriter(java.io.OutputStreamWriter(java.io.FileOutputStream(file), "UTF-8")).use { out ->
            out.print('\uFEFF')
            out.println("Дата;Категория;Сумма;Комментарий;Статус")
            for (t in manager.transactions) {
                out.println("${t.date};${t.category};${t.amount};${t.comment};${if(t.isCompleted)"Выполнено" else "Ожидает"}")
            }
        }
        JOptionPane.showMessageDialog(this, "Отчёт сохранён в ${file.absolutePath}")
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

        // пункт меню для экспорта в CSV
        val exportItem = JMenuItem("📤 Экспорт отчёта в CSV")
        exportItem.addActionListener { exportToCSV() }

        val exitItem = JMenuItem("🚪 Выход")
        exitItem.addActionListener { dispose() }

        actionsMenu.add(addItem)
        actionsMenu.add(viewAllItem)
        actionsMenu.add(editBudgetItem)
        actionsMenu.add(setMonthlyBudgetItem)
        actionsMenu.addSeparator()
        actionsMenu.add(analyticsItem)
        actionsMenu.add(exportItem)
        actionsMenu.addSeparator()
        actionsMenu.add(exitItem)

        val helpMenu = JMenu("❓ Справка")
        val aboutItem = JMenuItem("О программе")
        aboutItem.addActionListener {
            JOptionPane.showMessageDialog(this,
                "Планировщик трат\nВерсия 2.0\n\nФункции:\n- Добавление трат\n- Просмотр всех трат\n- Удаление трат\n- Отметка выполненных\n- Установка бюджета на месяц\n- Прогнозы и аналитика\n- Экспорт в CSV\n- Фильтр по дате\n- Умный ввод суммы\n- Автосохранение",
                "О программе", JOptionPane.INFORMATION_MESSAGE)
        }
        helpMenu.add(aboutItem)

        menuBar.add(actionsMenu)
        menuBar.add(helpMenu)
        setJMenuBar(menuBar)

        add(mainPanel)
    }

    private fun showSetMonthlyBudgetDialog()
    {
        val dialog = JDialog(this, "💰 Установить бюджет на месяц", true)
        dialog.setSize(350, 180)
        dialog.setLocationRelativeTo(this)
        dialog.layout = BorderLayout()

        val panel = JPanel().apply {
            layout = GridLayout(3, 1, 10, 10)
            border = EmptyBorder(20, 20, 20, 20)
        }

        val currentTotal = manager.getTotalBudget()
        panel.add(JLabel("💰 Текущий общий бюджет: ${String.format("%.2f", currentTotal)} ₽"))
        panel.add(JLabel("📝 Новый бюджет на месяц (₽):"))

        val budgetField = JTextField()
        panel.add(budgetField)

        val buttonPanel = JPanel()
        val saveButton = JButton("💾 Сохранить")
        val cancelButton = JButton("❌ Отмена")

        saveButton.addActionListener {
            val newBudget = budgetField.text.toDoubleOrNull()
            if (newBudget != null && newBudget > 0)
            {
                manager.setMonthlyBudget(newBudget)
                refreshAll()
                dialog.dispose()
                JOptionPane.showMessageDialog(this, "Бюджет обновлён! Категории пересчитаны пропорционально.")
            } else {
                JOptionPane.showMessageDialog(dialog, "Введите корректную сумму > 0", "Ошибка", JOptionPane.ERROR_MESSAGE)
            }
        }

        cancelButton.addActionListener { dialog.dispose() }

        buttonPanel.add(saveButton)
        buttonPanel.add(cancelButton)

        dialog.add(panel, BorderLayout.CENTER)
        dialog.add(buttonPanel, BorderLayout.SOUTH)
        dialog.isVisible = true
    }

    //диалог аналитики
    private fun showAnalyticsDialog()
    {
        val dialog = JDialog(this, "📈 Прогнозы и аналитика", true)
        dialog.setSize(500, 500)
        dialog.setLocationRelativeTo(this)
        dialog.layout = BorderLayout()

        val mainPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(20, 20, 20, 20)
        }

        val titleLabel = JLabel("📊 Анализ ваших трат").apply {
            font = Font("Arial", Font.BOLD, 18)
            alignmentX = java.awt.Component.CENTER_ALIGNMENT
        }
        mainPanel.add(titleLabel)
        mainPanel.add(Box.createVerticalStrut(20))

        // 1. Средние траты
        val avgDay = manager.getAverageSpentPerDay()
        val avgWeek = manager.getAverageSpentPerWeek()
        val avgMonth = manager.getAverageSpentPerMonth()

        val avgPanel = JPanel(GridLayout(3, 1, 5, 5)).apply {
            border = BorderFactory.createTitledBorder("📅 Средние траты")
            add(JLabel("  • В день: ${String.format("%.2f", avgDay)} ₽"))
            add(JLabel("  • В неделю: ${String.format("%.2f", avgWeek)} ₽"))
            add(JLabel("  • В месяц: ${String.format("%.2f", avgMonth)} ₽"))
        }
        mainPanel.add(avgPanel)
        mainPanel.add(Box.createVerticalStrut(15))

        // 2. Самая затратная категория
        val mostExpensive = manager.getMostExpensiveCategory()
        val mostExpensivePanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createTitledBorder("🏆 Самая затратная категория")
        }

        if (mostExpensive != null && mostExpensive.second > 0) {
            val percentage = manager.getCategoryPercentage(mostExpensive.first)
            val category = manager.categories.find { it.name == mostExpensive.first }
            mostExpensivePanel.add(JLabel("  ${category?.icon ?: "📌"} ${mostExpensive.first}"))
            mostExpensivePanel.add(JLabel("  Потрачено: ${String.format("%.2f", mostExpensive.second)} ₽"))
            mostExpensivePanel.add(JLabel("  Доля от всех трат: ${String.format("%.1f", percentage)}%"))
        } else
        {
            mostExpensivePanel.add(JLabel("Нет данных о тратах"))
        }
        mainPanel.add(mostExpensivePanel)
        mainPanel.add(Box.createVerticalStrut(15))

        // 3. Прогноз на конец месяца
        val forecast = manager.getMonthEndForecast()
        val totalBudget = manager.getTotalBudget()
        val totalSpent = manager.getTotalSpent()

        val forecastPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createTitledBorder("Прогноз на конец месяца")
        }

        val forecastColor = when
        {
            forecast > 0 -> Color.GREEN
            forecast < 0 -> Color.RED
            else -> Color.GRAY
        }

        val remainingPanel = JPanel().apply {
            layout = BorderLayout()
            add(JLabel("  Остаток бюджета сейчас: "), BorderLayout.WEST)
            add(JLabel("${String.format("%.2f", totalBudget - totalSpent)} ₽").apply {
                font = Font("Arial", Font.BOLD, 14)
            }, BorderLayout.EAST)
        }

        val forecastResultPanel = JPanel().apply {
            layout = BorderLayout()
            add(JLabel("  Прогнозируемый остаток: "), BorderLayout.WEST)
            add(JLabel("${String.format("%.2f", forecast)} ₽").apply {
                font = Font("Arial", Font.BOLD, 14)
                foreground = forecastColor
            }, BorderLayout.EAST)
        }

        val statusLabel = when
        {
            forecast > 5000 -> JLabel("✅ Отлично, вы уложитесь в бюджет с запасом")
            forecast > 0 -> JLabel("👍 Хорошо, вы уложитесь в бюджет")
            forecast > -5000 -> JLabel("⚠️ Внимание! Возможно небольшое превышение бюджета")
            else -> JLabel("🔴 Критично! Серьёзное превышение бюджета")
        }

        forecastPanel.add(remainingPanel)
        forecastPanel.add(forecastResultPanel)
        forecastPanel.add(Box.createVerticalStrut(10))
        forecastPanel.add(statusLabel)

        mainPanel.add(forecastPanel)
        mainPanel.add(Box.createVerticalStrut(15))

        // 4. Доп. статистика
        val statsPanel = JPanel(GridLayout(0, 1, 5, 5)).apply {
            border = BorderFactory.createTitledBorder("📊 Дополнительная статистика")
        }

        val totalTransactions = manager.transactions.size
        val avgTransaction = if (totalTransactions > 0) totalSpent / totalTransactions else 0.0
        val daysWithTransactions = manager.transactions.map { it.date }.distinct().size

        statsPanel.add(JLabel("  • Всего трат: $totalTransactions"))
        statsPanel.add(JLabel("  • Средний чек: ${String.format("%.2f", avgTransaction)} ₽"))
        statsPanel.add(JLabel("  • Дней с тратами: $daysWithTransactions"))

        mainPanel.add(statsPanel)

        val closeButton = JButton("Закрыть")
        closeButton.addActionListener { dialog.dispose() }

        val scrollPane = JScrollPane(mainPanel)
        scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED

        dialog.add(scrollPane, BorderLayout.CENTER)
        dialog.add(closeButton, BorderLayout.SOUTH)
        dialog.isVisible = true
    }

    //контекстное меню для трат
    private fun showTransactionContextMenu(index: Int, x: Int, y: Int)
    {
        val popup = JPopupMenu()

        val deleteItem = JMenuItem("🗑️ Удалить трату")
        deleteItem.addActionListener {
            manager.deleteTransaction(index)
            refreshAll()
        }

        val completeItem = JMenuItem("✅ Отметить как выполненную")
        completeItem.addActionListener {
            manager.markTransactionCompleted(index)
            refreshAll()
        }

        popup.add(deleteItem)
        popup.add(completeItem)
        popup.show(recentList, x, y)
    }

    // фильтр по дате в диалоге "Все траты"
    private fun showAllTransactionsDialog()
    {
        val dialog = JDialog(this, "📜 Все траты", true)
        dialog.setSize(650, 550)
        dialog.setLocationRelativeTo(this)
        dialog.layout = BorderLayout()

        // Панель фильтров
        val filterPanel = JPanel().apply {
            layout = FlowLayout(FlowLayout.LEFT)
            border = EmptyBorder(10, 10, 5, 10)
        }

        filterPanel.add(JLabel("Фильтр по дате:"))
        val filterCombo = JComboBox(arrayOf("За всё время", "За неделю", "За месяц", "За год"))
        filterPanel.add(filterCombo)

        val model = DefaultListModel<String>()
        val list = JList(model)
        list.font = Font("Monospaced", Font.PLAIN, 12)
        list.fixedCellHeight = 35

        fun updateList() {
            model.clear()
            val now = LocalDate.now()
            val filtered = when (filterCombo.selectedIndex) {
                1 -> manager.transactions.filter { it.date >= now.minusWeeks(1) }
                2 -> manager.transactions.filter { it.date >= now.minusMonths(1) }
                3 -> manager.transactions.filter { it.date >= now.minusYears(1) }
                else -> manager.transactions
            }
            for ((idx, t) in filtered.withIndex()) {
                val status = if (t.isCompleted) "✅" else "⏳"
                val commentStr = if (t.comment.isNotEmpty()) " | ${t.comment}" else ""
                val dateStr = t.date.format(dateFormatter)
                model.addElement("$status ${t.amount.toInt()} ₽ | ${t.category} | $dateStr$commentStr")
            }
        }

        filterCombo.addActionListener { updateList() }
        updateList()

        list.addMouseListener(object : java.awt.event.MouseAdapter()
        {
            override fun mouseClicked(e: java.awt.event.MouseEvent)
            {
                if (e.button == java.awt.event.MouseEvent.BUTTON3)
                {
                    val idx = list.locationToIndex(e.point)
                    if (idx != -1)
                    {
                        val popup = JPopupMenu()
                        val deleteItem = JMenuItem("🗑️ Удалить")
                        deleteItem.addActionListener {
                            // Нужно найти реальный индекс в оригинальном списке
                            val now = LocalDate.now()
                            val filtered = when (filterCombo.selectedIndex) {
                                1 -> manager.transactions.filter { it.date >= now.minusWeeks(1) }
                                2 -> manager.transactions.filter { it.date >= now.minusMonths(1) }
                                3 -> manager.transactions.filter { it.date >= now.minusYears(1) }
                                else -> manager.transactions
                            }
                            val realIndex = manager.transactions.indexOf(filtered[idx])
                            if (realIndex != -1) {
                                manager.deleteTransaction(realIndex)
                                updateList()
                                refreshAll()
                            }
                            dialog.dispose()
                            showAllTransactionsDialog()
                        }
                        popup.add(deleteItem)
                        popup.show(list, e.x, e.y)
                    }
                }
            }
        })

        val scrollPane = JScrollPane(list)
        dialog.add(filterPanel, BorderLayout.NORTH)
        dialog.add(scrollPane, BorderLayout.CENTER)

        val closeButton = JButton("Закрыть")
        closeButton.addActionListener { dialog.dispose() }
        dialog.add(closeButton, BorderLayout.SOUTH)

        dialog.isVisible = true
    }

    private fun showEditBudgetDialog() {
        val dialog = JDialog(this, "✏️ Редактировать бюджеты категорий", true)
        dialog.setSize(400, 400)
        dialog.setLocationRelativeTo(this)
        dialog.layout = BorderLayout()

        val panel = JPanel().apply {
            layout = GridLayout(manager.categories.size, 2, 10, 10)
            border = EmptyBorder(20, 20, 20, 20)
        }

        val budgetFields = mutableMapOf<String, JTextField>()

        for (cat in manager.categories)
        {
            panel.add(JLabel("${cat.icon} ${cat.name}:"))
            val field = JTextField(cat.budget.toInt().toString())
            budgetFields[cat.name] = field
            panel.add(field)
        }

        val saveButton = JButton("💾 Сохранить")
        saveButton.addActionListener {
            for ((name, field) in budgetFields)
            {
                val newBudget = field.text.toDoubleOrNull()
                if (newBudget != null && newBudget > 0)
                {
                    manager.updateBudget(name, newBudget)
                }
            }
            refreshAll()
            dialog.dispose()
            JOptionPane.showMessageDialog(this, "Бюджеты обновлены")
        }

        dialog.add(panel, BorderLayout.CENTER)
        dialog.add(saveButton, BorderLayout.SOUTH)
        dialog.isVisible = true
    }

    private fun refreshAll()
    {
        refreshCategories()
        refreshRecentTransactions()
    }

    private fun refreshCategories()
    {
        categoryPanel.removeAll()

        for (category in manager.categories)
        {
            val card = createCategoryCard(category)
            categoryPanel.add(card)
            categoryPanel.add(Box.createVerticalStrut(10))
        }

        val totalBudget = manager.getTotalBudget()
        val totalSpent = manager.getTotalSpent()
        val totalPercent = if (totalBudget > 0) ((totalSpent / totalBudget) * 100).toInt() else 0

        val totalCard = JPanel().apply {
            layout = BorderLayout()
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                EmptyBorder(10, 10, 10, 10)
            )
            background = Color(230, 240, 255)
        }

        totalCard.add(JLabel("📌 ИТОГО: ${totalSpent.toInt()} ₽ из ${totalBudget.toInt()} ₽"), BorderLayout.WEST)
        totalCard.add(JLabel("$totalPercent%"), BorderLayout.EAST)

        categoryPanel.add(totalCard)
        categoryPanel.revalidate()
        categoryPanel.repaint()
    }

    private fun createCategoryCard(category: Category): JPanel
    {
        val percent = if (category.budget > 0) ((category.spent / category.budget) * 100).coerceAtMost(100.0).toInt() else 0

        val progressColor = when
        {
            category.spent > category.budget -> Color.RED
            category.spent > category.budget * 0.8 -> Color.ORANGE
            else -> Color.GREEN
        }

        val progressBar = JProgressBar(0, 100).apply {
            value = percent
            background = Color.LIGHT_GRAY
            foreground = progressColor
            preferredSize = Dimension(200, 20)
        }

        val panel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY),
                EmptyBorder(10, 10, 10, 10)
            )
        }

        val topPanel = JPanel(BorderLayout())
        topPanel.add(JLabel("${category.icon} ${category.name}"), BorderLayout.WEST)
        topPanel.add(JLabel("${category.spent.toInt()} / ${category.budget.toInt()} ₽"), BorderLayout.EAST)

        panel.add(topPanel, BorderLayout.NORTH)
        panel.add(progressBar, BorderLayout.CENTER)

        return panel
    }

    private fun refreshRecentTransactions()
    {
        recentListModel.clear()
        for (t in manager.transactions.take(5))
        {
            val status = if (t.isCompleted) "✅" else "⏳"
            val commentStr = if (t.comment.isNotEmpty()) " | ${t.comment}" else ""
            recentListModel.addElement("$status ${t.amount.toInt()} ₽ | ${t.category} | ${t.date.format(dateFormatter)}$commentStr")
        }
        if (manager.transactions.isEmpty())
        {
            recentListModel.addElement("Нет добавленных трат. Нажмите 'Добавить трату' в меню")
        }
    }

    // умный ввод суммы используется в диалоге добавления
    private fun showAddDialog()
    {
        val dialog = JDialog(this, "➕ Добавить трату", true)
        dialog.setSize(350, 400)
        dialog.setLocationRelativeTo(this)
        dialog.layout = BorderLayout()

        val fieldsPanel = JPanel().apply {
            layout = GridLayout(0, 2, 10, 10)
            border = EmptyBorder(20, 20, 20, 20)
        }

        val amountField = JTextField()
        amountField.toolTipText = "Можно писать: 1500, 1.5к, 2т (к = тысячи)"  // ДОБАВЛЕНО
        val categoryCombo = JComboBox(manager.categories.map { it.name }.toTypedArray())
        val commentField = JTextField()
        val dateField = JTextField(LocalDate.now().format(dateFormatter))

        fieldsPanel.add(JLabel("💰 Сумма (₽):"))
        fieldsPanel.add(amountField)
        fieldsPanel.add(JLabel("📂 Категория:"))
        fieldsPanel.add(categoryCombo)
        fieldsPanel.add(JLabel("📝 Комментарий (опционально):"))
        fieldsPanel.add(commentField)
        fieldsPanel.add(JLabel("📅 Дата (ДД.ММ.ГГГГ):"))
        fieldsPanel.add(dateField)

        val buttonPanel = JPanel().apply {
            border = EmptyBorder(0, 20, 20, 20)
        }

        val saveButton = JButton("💾 Сохранить")
        val cancelButton = JButton("❌ Отмена")

        saveButton.addActionListener {
            try
            {
                // использование умного ввода суммы
                val amount = parseAmount(amountField.text)
                if (amount <= 0)
                {
                    JOptionPane.showMessageDialog(dialog, "Введите корректную сумму > 0\nПримеры: 1500, 1.5к, 2т", "Ошибка", JOptionPane.ERROR_MESSAGE)
                    return@addActionListener
                }

                val category = categoryCombo.selectedItem as String
                val comment = commentField.text
                val date = try
                {
                    LocalDate.parse(dateField.text, dateFormatter)
                } catch (e: Exception)
                {
                    LocalDate.now()
                }

                manager.addTransaction(amount, category, comment, date)
                refreshAll()
                dialog.dispose()

            } catch (e: Exception)
            {
                JOptionPane.showMessageDialog(dialog, "Ошибка: ${e.message}", "Ошибка", JOptionPane.ERROR_MESSAGE)
            }
        }

        cancelButton.addActionListener { dialog.dispose() }

        buttonPanel.add(saveButton)
        buttonPanel.add(cancelButton)

        dialog.add(fieldsPanel, BorderLayout.CENTER)
        dialog.add(buttonPanel, BorderLayout.SOUTH)
        dialog.isVisible = true
    }
}

fun main()
{
    SwingUtilities.invokeLater {
        BudgetApp().isVisible = true
    }
}