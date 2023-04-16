package com.example.coroutinestartandroid

import android.accounts.NetworkErrorException
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.coroutinestartandroid.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.coroutines.*


/**
 * Компоненты корутины: Job, Context, Continuation, Dispatcher и CoroutineScope.
 * Job - это управление корутиной и ее статус.
 * Continuation - код, который должна выполнять корутина.
 * Корутина - это не какой-то конкретный объект. Это набор объектов, которые взаимодействуют друг с другом.
 * Жизненный цикл корутины состоит из трех этапов: создание, запуск, завершение.
 * launch {} - выполняются первые 2 этапа.
 *  ------------------------------------------------------------------------------------------------
 * Ключевое слово - suspend даст Котлину понять, что это suspend функция и она будет приостанавливать корутину.
 * ------------------------------------------------------------------------------------------------
 * Continuation - код, который должна выполнять корутина.
 * В результате преобразования кода корутины(launch, async) в Java будет создан Continuation класс
 * ------------------------------------------------------------------------------------------------
 * Job - это управление корутиной и ее статус.
 * Job хранит в себе состояние корутины: активна/отменена/завершена.
 * job.cancel() - не отменяет корутину, а только меняет её статус. Нужно проверять через isActive.(так
 * рабатает если использовать например TimeUnit.MILLISECONDS.sleep(1000), но если внутри билдера будет
 * suspend функция, то все отменяется)!!!
 * ------------------------------------------------------------------------------------------------
 * Scope - то такой родитель для всех корутин. Когда мы отменяем scope, мы отменяем все его дочерние
 * корутины. Scope можно взять у объкта с жизненным циклом(Activity, ViewModel). Что бы запустить
 * корутину нужен scope.
 *
 * В любой корутине есть свой CoroutineScope. В качестве обязательного Job, этот scope использует Job корутины.
 * Job является scope, и сам же выступает в качестве Job этого scope.
 * scope.cancel(), мы отменяем Job-родитель, а это отменит все Job-ы корутин, созданных с помощью этого scope.
 *
 * Каждая корутина создает внутри себя свой scope, чтобы иметь возможность запускать дочерние корутины.
 * Именно этот scope и доступен нам как this в блоке кода корутины.
 * У каждого scope должен быть Job, который будет выступать родительским для создаваемых корутин.
 * Когда мы в родительской корутине создаем дочернюю, Job дочерней корутины подписывается на Job
 * родительской. И если отменить родительскую корутину, то отменится и дочерняя.
 *
 * Job является scope, и сам же выступает в качестве Job этого scope.
 * Проверка: val job = scope.launch {
     Log.d(TAG, "scope = $this")
       }
     Log.d(TAG, "job = $job") - выведет один и тот же объект.
 *
 * ------------------------------------------------------------------------------------------------
 * Билдер упакует переданный ему блок кода в корутину и запустит ее.
 * Билдер launch() не возворащает результат своей работы.
 * Билдер async() возворащает результат своей работы.
 * ------------------------------------------------------------------------------------------------
 * Context - это хранилище, похож на Map. В нем зачастую хранится Job и Dispatchers.
 * Пример создания context:
 * val context = Job() + Dispatchers.Default
 * val scope = CoroutineScope(context)
 * Когда мы создаем scope и передаем ему контекст, выполняется проверка, что этот контекст содержит Job.
 * И если не содержит, то Job будет создан автомотически.
 * ------------------------------------------------------------------------------------------------
 * Dispatchers:
 * Default - Этот диспетчер представляет собой пул потоков. Количество потоков равно количеству
 * ядер процессора. Используется по умолчанию. Он не подходит для IO операций, но сгодится для интенсивных вычислений.
 *
 * IO - Использует тот же пул потоков, что и диспетчер по умолчанию. Но его лимит на потоки равен 64
 * (или числу ядер процессора, если их больше 64).
 *
 * IO операции не требуют больших затрат CPU. Мы можем запустить их хоть 10 или 100 одновременно,
 * чтобы они суммарно выполнились быстрее. И при этом мы не особо нагрузим процессор, потому что
 * основное время там тратится на ожидание и работу с дискоим или сетью.
 * Если мы будем запускать такие операции в Default диспетчере, то мы тем самым ограничим количество
 * одновременно выполняемых операций.
 *
 * Main - Main диспетчер запустит корутину в основном потоке.
 * Main.immediate - см. launchMainImmediateDispatcher()
 *
 * Unconfined - не переключает поток(см. launchInMainThread())
 *
 * Создание своего dispatcher
 * val scope = CoroutineScope(Executors.newSingleThreadExecutor().asCoroutineDispatcher())
 *
 * ------------------------------------------------------------------------------------------------
 * Отмена корутины.
 * При вызове cancel() корутина отправляет CancellationException. Эту ошибку корутина шлет
 * родительской и дочерним корутинам в уведомлениях о своей отмене.
 * Когда ошибка пришла родительской корутине, та проверяет ошибку и если это CancellationException,
 * то никак не реагирует.
 * Когда ошибко пришла дочерней корутине, то дочерняя корутина ничего не проверяет и отменяется сама
 * и все свои дочерние корутины.
 * suspend fun suspendCoroutine - не отменяются при отмене корутины.
 * suspend fun suspendCancellableCoroutine - suspend функция получает уведомление об отмене с
 * CancellationException и сразу шлет этот Exception в Continuation, как результат своей работы.
 *
 * ------------------------------------------------------------------------------------------------
 * coroutineScope, supervisorScope, withContext, runBlocking - это suspend функции, внутри которых
 * запускается специальная корутина. (Особо не понятно!!! Урок 17)
 * coroutineScope - при возникновении ошибки не передает ошибку на верх своему родеилю.
 * supervisorScope - не принимает ошибку от дочерней корутины, и та сама пытается её обработать с
 * помощью CoroutineExceptionHandler если он есть или краш, если нет.
 * withContext - coroutineScope с возможностью добавить/заменить элементы контекста. Позволяет
 * переключить поток выполнения кода, дождаться результата и вернуться обратно в свой поток.
 * runBlocking - запускает корутину, которая блокирует текущий поток, пока не завершит свою работу
 * (и пока не дождется завершения дочерних корутин). Нужен для тестов, в проде не использвать.
 * ------------------------------------------------------------------------------------------------
 * viewModelScope - привязана к жизненому цыклу ViewModel.
 * lifecycleScope - привязана к жизненому цыклу LifecycleOwner.
 * MainScope - не привязан ни к чему, нужно самим следить за его отменой.
 */

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val TAG = "TAG ${this.javaClass.simpleName}"
    private var formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val scope = CoroutineScope(Job())
    private lateinit var job: Job
    private lateinit var startLazyJob: Job
    private val viewModel: MainViewModel by viewModels()



    @OptIn(InternalCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log("onCreate()")
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
       // lessonSeven()
        setListeners()
    }

    override fun onStart() {
        super.onStart()
        log("onStart()")
    }

    override fun onResume() {
        super.onResume()
        log("onResume()")
    }

    /**
     * когда мы отменяем корутину scope.cancel(), то мы таким образом отменяем job который находится
     * внутри корутины и все дочерние jobs.
     */
    override fun onDestroy() {
        super.onDestroy()
        log("onDestroy()")
        scope.cancel()
    }

    @InternalCoroutinesApi
    private fun setListeners() {
        with(binding) {
            bntRun.setOnClickListener { onRun()}
            btnCancel.setOnClickListener { onCancel()}
            bntRunNotWaitChild.setOnClickListener { onRunNotWaitChild()}
            bntParallelRanTwoCoroutine.setOnClickListener { onParallelRanTwoCoroutine()}
            bntRunWaitUntilChild.setOnClickListener { onRunWaitUntilChild()}
            bntStartCoroutine.setOnClickListener { onStartCoroutine()}
            bntCreateCoroutine.setOnClickListener { onCreateCoroutine()}
            bntAsyncStart.setOnClickListener { onAsyncStart()}
            bntAsyncParallel.setOnClickListener { onAsyncParallel()}
            bntRunContext.setOnClickListener { onRunContext()}
            bntNestedCoroutines.setOnClickListener { nestedCoroutines()}
            bntDefault.setOnClickListener { testDefaultDispatcher()}
            bntIO.setOnClickListener { testIODispatcher()}
            bntMain.setOnClickListener { launchInMainThread()}
            bntMainImmediate.setOnClickListener { launchMainImmediateDispatcher()}
            bntParentChild.setOnClickListener { relationshipBetweenParentAndChildCoroutine()}
            bntThrowException.setOnClickListener { throwException()}
            bntHandleExceptionCancelChild.setOnClickListener { handleExceptionCancelChildCoroutine()}
            bntHandleExceptionNotCancelChild.setOnClickListener { handleExceptionNotCancelChildCoroutine()}
            bntThrowExceptionChildCoroutine.setOnClickListener { throwExceptionChildCoroutine()}
            bntThrowExceptionAsync.setOnClickListener { errorAsync()}
            bntThrowExceptionSuspend.setOnClickListener { throwExceptionSuspend()}
            bntChannelSend.setOnClickListener {
                channelSend()
                channelReceive()
            }
           // bntChannelReceive.setOnClickListener { channelReceive()}
            bntStartFlow.setOnClickListener { startFlow()}
            bntCatch.setOnClickListener { catchExample()}
            bntRetry.setOnClickListener { retryExample()}
            bntRetryWhen.setOnClickListener { retryWhenExample()}
            bntLaunchCoroutineVM.setOnClickListener { launchCoroutineFromViewModel()}
            bntLaunchCoroutineLifecycleScope.setOnClickListener { launchCoroutineFromLifecycleScope()}
        }
    }

    private fun log(text: String) {
        Log.d(TAG, "${formatter.format(Date())} $text [${Thread.currentThread().name}]")
    }

// Lesson 7 ---------------------------------------------------------------------------------------
    private fun lessonSeven() {
        scope.launch {
            Log.d(TAG, "lessonSeven() launch")
        }

        val job = scope.launch {
            Log.d(TAG, "lessonSeven() scope = $this")
        }
        Log.d(TAG, "lessonSeven() job = $job")
    }

// Lesson 8 ---------------------------------------------------------------------------------------

    private fun onRun() {
        log("onRun, start")

        job = scope.launch {
            log("coroutine, start")
            var x = 0
            while (x < 5 && isActive) {
                delay(1000)
                log("coroutine, ${x++}, isActive = ${isActive}")
            }
            log("coroutine, end")
        }

        log("onRun, end")
    }

    /**
     * job.cancel() - отменяет все дочерние jobs
     */
    private fun onCancel(){
        log("onCancel")
        job.cancel()
    }

// Lesson 9 Builders--------------------------------------------------------------------------------

    /**
     * Родительская корутина не ждет выпиоление дочерней
     */
    private fun onRunNotWaitChild() {
        scope.launch {
            log("parent coroutine, start")

            launch {
                log("child coroutine, start")
                TimeUnit.MILLISECONDS.sleep(1000)
                log("child coroutine, end")
            }

            log("parent coroutine, end")
        }
    }

    /**
     * Запуск родительской корутины не в main потоке!!!.
     * В данном методе запусткается родительская корутина в например в потоке DefaultDispatcher-worker-1.
     * Родительская корутина запусктае дочернюю корутину, которая запускается в потоке DefaultDispatcher-worker-2.
     * Родительская корутина ждет выполнения дочерней(метод join()). Сначала завершается дочерняя корутина в DefaultDispatcher-worker-2
     * потоке, а потом родительская в DefaultDispatcher-worker-2!!! потоке.
     *
     * Запуск родительской корутины в main потоке.
     * Поведения такое же как описано выше, но родительская корутина завершится в main потоке!!!.
     */
    private fun onRunWaitUntilChild() {
        scope.launch(Dispatchers.IO) {
            log("parent coroutine, start")

            val job = launch(Dispatchers.IO) {
                log("child coroutine, start")
                TimeUnit.MILLISECONDS.sleep(1000)
                log("child coroutine, end")
            }

            log("parent coroutine, wait until child completes")
            job.join()

            log("parent coroutine, end")
        }
    }

    /**
     * Паралельный запуск 2-х дочерних корутин.
     */
    private fun onParallelRanTwoCoroutine() {
        scope.launch {
            log("parent coroutine, start")

            val job = launch {
                log("child coroutine1, start")
                TimeUnit.MILLISECONDS.sleep(1000)
                log("child coroutine1, end")
            }

            val job2 = launch {
                log("child coroutine2, start")
                TimeUnit.MILLISECONDS.sleep(1500)
                log("child coroutine2, end")
            }

            log("parent coroutine, wait until children complete")
            job.join()
            job2.join()

            log("parent coroutine, end")
        }
    }

    /**
     * Метод создает, но не запускает корутину.
     */
    private fun onCreateCoroutine() {
        log("onCreateCoroutine, start")

        startLazyJob = scope.launch(start = CoroutineStart.LAZY) {
            log("coroutine, start")
            TimeUnit.MILLISECONDS.sleep(1000)
            log("coroutine, end")
        }
        log("onCreateCoroutine, end")
    }

    /**
     * Метод запускает ранее созданую корутину.
     */
    private fun onStartCoroutine() {
        log("onStartCoroutine(), start")
        startLazyJob.start()
        log("onStartCoroutine(), end")
    }

    /**
     * Запуск корутины с помощью билдера async, который возвращает результат.
     */
    private fun onAsyncStart() {
        log("onAsyncStart(), start")
        scope.launch {
            log("parent coroutine, start")

            val deferred = async() {
                log("child coroutine, start")
                TimeUnit.MILLISECONDS.sleep(1000)
                log("child coroutine, end")
                "async result"
            }

            log("parent coroutine, wait until child returns result")
            val result = deferred.await()
            log("parent coroutine, child returns: $result")

            log("parent coroutine, end")
        }
        log("onAsyncStart(), end")
    }

    /**
     * Паралельный запуск двух корутин с получение результата.
     */
    private fun onAsyncParallel() {
        scope.launch(Dispatchers.IO) {
            log("parent coroutine, start")
            val data = async { getData() }
            val data2 = async { getData2() }

            log("parent coroutine, wait until children return result")
            val result = "${data.await()}, ${ data2.await()}"
            log("parent coroutine, children returned: $result")

            log("parent coroutine, end")
        }
    }

    private suspend fun getData(): String {
        log("getData")
        delay(1000)
        return "data"
    }

    private suspend fun getData2(): String {
        delay(1500)
        return "data2"
    }

// Lesson 10 Context-------------------------------------------------------------------------------

    private fun contextToString(context: CoroutineContext): String =
        "Job = ${context[Job]}, Dispatcher = ${context[ContinuationInterceptor]}"

    /**
     * Если в scope корутины не передать Dispatchers, то в билдере scope возьмет Dispatchers.Default,
     * если передать, то переданный.
     */
    private fun onRunContext() {
        val scope = CoroutineScope(Dispatchers.Main)
        log("scope, ${contextToString(scope.coroutineContext)}")

        scope.launch {
            log("coroutine, ${contextToString(coroutineContext)}")
        }
    }

    /**
     * Диспетчер Main передается по цепочке: от scope к level1, от level1 к level2, от level2 к level3.
     */
    private fun nestedCoroutines() {
        val scope = CoroutineScope(Job() + Dispatchers.Main)
        log("scope, ${contextToString(scope.coroutineContext)}")

        scope.launch() {
            log("coroutine, level1, ${contextToString(coroutineContext)}")

            launch {
                log("coroutine, level2, ${contextToString(coroutineContext)}")

                launch() {
                    log("coroutine, level3, ${contextToString(coroutineContext)}")
                }
            }
        }
    }

    // Lesson 11 Dispatchers------------------------------------------------------------------------
    private fun testDefaultDispatcher() {
        val scope = CoroutineScope(Dispatchers.Default)

        repeat(10) {
            scope.launch {
                log("coroutine $it, start")
                TimeUnit.MILLISECONDS.sleep(100)
                log("coroutine $it, end")
            }
        }
    }

    private fun testIODispatcher() {
        val scope = CoroutineScope(Dispatchers.IO)

        repeat(65) {
            scope.launch {
                log("coroutine $it, start")
                TimeUnit.MILLISECONDS.sleep(1000)
                log("coroutine $it, end")
            }
        }
    }

    /**
     *При запуске корутины в потоках Dispatchers.IO, Main, Default - результат "log("end coroutine result = $data")"
     *  вернется в томже потоке, но если запустить в Dispatchers.Unconfined, то результат
     *  " log("suspend function, background work")" вернеться в потоке в котором выполнялся код.
     */
     private fun launchInMainThread() {
         val scope = CoroutineScope(Dispatchers.Unconfined)
         scope.launch {
             log("start coroutine")
             val data = getDataFromThread()
             log("end coroutine result = $data")
         }
    }

    private suspend fun getDataFromThread(): String {
      return  suspendCoroutine {
          log("suspend function, start")
            thread {
                log("suspend function, background work")
                TimeUnit.MILLISECONDS.sleep(1000)
                it.resume("Data")
            }
        }
    }

    /**
     * Dispatchers.Main - сначала выполняется код "log("before")" -> создается корутина (launch{}) и
     * передается диспетчеру потока Main(и ждет пока поток освободится) -> выполняется код "log("after")" ->
     * поток освободился, выполняется код корутины. Результат: before -> after -> launch.
     *
     * Dispatchers.Main.immediate - проверяет в каком потока запущена работа launch{}, если Main, то
     * нет смысла помещать корутину в очеред и выполняет сразу. Результат: before -> launch -> after .
     *
     */
    private fun launchMainImmediateDispatcher() {
        log("before")
        scope.launch(Dispatchers.Main.immediate) {
            log("launch")
        }
        log("after")
    }

    // Lesson 12 Связь между родительской и дочерней корутиной -------------------------------------

    /**
     * Родительская корутина выполнила свой код и ждет пока выполнится дочерняя и только потом
     * завершаеться.
     */
    private fun relationshipBetweenParentAndChildCoroutine() {
        val job = scope.launch {
            log("parent start")
            launch {
                log("child start")
                delay(1000)
                log("child end")
            }
            log("parent end")
        }

        scope.launch {
            delay(500)
            log("parent job is active: ${job.isActive}")
            delay(1000)
            log("parent job is active: ${job.isActive}")
        }
    }

    // Lesson 13 Exception -------------------------------------------------------------------------

    private fun throwException() {
        /**
         * crash app
         */
        /* scope.launch {
             Integer.parseInt("a")
         }*/

        /**
         * error will be caught
         */
        scope.launch {
            try {
                Integer.parseInt("a")
            } catch (e: Exception) {
                log("exception $e")
            }
        }
    }

    /**
     * Корутина, в которой произошло исключение, сообщит об ошибке в родительский scope,
     * а тот отменит все свои дочерние корутины.
     */
    private fun handleExceptionCancelChildCoroutine() {
        val handler = CoroutineExceptionHandler { context, exception ->
            log("first coroutine exception $exception")
        }

        scope.launch(handler) {
            TimeUnit.MILLISECONDS.sleep(1000)
            Integer.parseInt("a")
        }

        scope.launch {
            repeat(5) {
                TimeUnit.MILLISECONDS.sleep(300)
                log("second coroutine isActive $isActive")
            }
        }
    }

    /**
     * При изпользовании SupervisorJob() не отменяется все дочерние корутины при возникновении ошибки в одной из них.
     */
    private fun handleExceptionNotCancelChildCoroutine() {
        val handler = CoroutineExceptionHandler { context, exception ->
            log("first coroutine exception $exception")
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + handler)

        scope.launch {
            TimeUnit.MILLISECONDS.sleep(1000)
            Integer.parseInt("a")
        }

        scope.launch {
            repeat(5) {
                TimeUnit.MILLISECONDS.sleep(300)
                log("second coroutine isActive ${isActive}")
            }
        }
    }

    // Lesson 14 Exception Nested Coroutines -------------------------------------------------------

    /**
     * Когда в дочерней корутине возникает ошибка, то дочерняя корутина передает эту ошибку своему
     * родителю и спрашивает, сможет ли родитель обработать эту ошибку.
     * Если родитель:
     * - скоуп, то родитель отвечает, что не сможет, тогда корутина пытаеться обработать сама и
     * отправляет ошибку в обработчик CoroutineExceptionHandler, если обработчика нет приложение падает.
     * (scope.launch(handler)) - засетить обработчик
     * - корутина, то родитель отвечаето, что сможет обработать ошибку, и дочерняя корутина передает
     * ошибку родителю, родительская корутина дальше по цепочке спрашивает у своего родителя и
     * передает ему ошибу пока не достигнет родителя скоупа. При этом каждий родитель будет отменяться
     * сам и отменять все свой дочерние корутины.
     *
     * При изпользовании SupervisorJob() будут отменены все дочерние корутины, кроме других дочерних
     * корутин скоупа.
     */

    private fun throwExceptionChildCoroutine() {
        val handler = CoroutineExceptionHandler { context, exception ->
            log("$exception was handled in Coroutine_${context[CoroutineName]?.name}")
        }

        val localScope = CoroutineScope(Job() + Dispatchers.Default + handler)

        localScope.launch(CoroutineName("1")) {

            launch(CoroutineName("1_1")) {
                TimeUnit.MILLISECONDS.sleep(1000)
                log("exception")
                Integer.parseInt("a")
            }

            launch(CoroutineName("1_2")) { repeatIsActive() }

            repeatIsActive()
        }

        localScope.launch(CoroutineName("2")) {

            launch(CoroutineName("2_1")) { repeatIsActive() }

            launch(CoroutineName("2_2")) { repeatIsActive() }

            repeatIsActive()
        }
    }

    private fun CoroutineScope.repeatIsActive() {
        repeat(5) {
            TimeUnit.MILLISECONDS.sleep(300)
            log("Coroutine_${coroutineContext[CoroutineName]?.name} isActive $isActive")
        }
    }

    // Lesson 15 Exception Async, Suspend ----------------------------------------------------------

    /**
     * Поведение билдера async по передаче ошибки от дочерней корутины в родительскую такоеже как
     * и у launch.
     */

    private fun errorAsync() {
        /**
         * если не обернуть await в try/catch, то код после await не выполниться, иначе код отработает
         */
        scope.launch {
            val deferred = async {
                Integer.parseInt("a")
            }
            try {
                val result = deferred.await()
                log("result $result")
            } catch (e: Exception) {
                log("error $e")
            }
            log("launch end")
        }
    }

    /**
     * Если синхронный код выбросит исключение, то это будет равносильно тому, что suspend функция
     * в корутине выбросила исключение.
     * Если асинхронный код выбросит исключение, то try/catch не спасет, будет краш, т.к. ошибка
     * произойдет в другом потоке.
     */
    private fun throwExceptionSuspend() {
        val localScope = CoroutineScope(Dispatchers.IO)
        localScope.launch {
            try {
                someFunction()
            } catch (e: Exception) {
            }
        }
    }

    private suspend fun someFunction(): String =
        suspendCancellableCoroutine { continuation ->
              //  continuation.resume("result")
            throwError()
        }

    private fun throwError() {
        scope.launch(Dispatchers.IO) {  Integer.parseInt("a") }
    }

    // Lesson 18 Channel ---------------------------------------------------------------------------
    /**
     * Канал используется, как средство передачи данных между корутинами. Т.е. он является ячейкой,
     * куда одна корутина может поместить данные, а другая корутина - взять их оттуда. Каналы потокобезопастны.
     *
     * Если первая корутина пытается отправить данные методом send, но вторая корутина еще не вызвала
     * receive, то метод send приостановит первую корутину и будет ждать. Аналогично наоборот.
     * Если вторая корутина уже вызвала receive, чтобы получить данные, но первая корутина еще не
     * отправила их, то метод receive приостановит вторую корутину.
     *
     * BroadcastChannel - Все данные, отправляемые в обычный канал будут равномерно распределены между получателями.
     * Т.е. если получатель один - он получит все данные. А если получателей несколько, то данные будут
     * разделены между ними, и каждый получит только часть от всех отправляемых данных.
     */

    private val channel = Channel<Int>()

    private fun channelSend() {
        scope.launch {
            delay(1000)
            log("send 5")
            channel.send(5)
            log("send done")
        }
    }

    private fun channelReceive() {
        scope.launch {
            delay(300)
            log("receive")
            val result = channel.receive()
            log("receive $result, done")
        }
    }

    // Flow ----------------------------------------------------------------------------------------
    /**
     * Flow - это cold источник данных
     * Билдеры Flow: flow{emit(1)}, asFlow, flowOf
     * Операторы Flow:
     * Intermediate - добавляют в Flow различные преобразования данных, но не запускают его.
     * (map, filter, take, zip, combine, withIndex, scan, debounce, distinctUntilChanged, drop, sample,
     * onEach, onStart, OnCompletion, onEmpty)
     * Эти операторы преобразуют данные и вернут новый Flow, но не запустит его.
     * Terminal - запускают Flow и работают с результатом его работы. Результатом работы являются данные.
     * (collect, single, reduce, count, first, toList, toSet, fold)
     */
    // Lesson 20 Flow ------------------------------------------------------------------------------

    @InternalCoroutinesApi
    private fun startFlow() {
        val flowStrings = flow {
            emit("abc")
            emit("def")
            emit("ghi")
        }
        scope.launch {
           flowStrings.toUpperCase().onEach {
                log("onEach $it")
            }.collect {
                log("collect $it")
            }
          /*  val res = flowStrings.join()
            log(res)*/
        }
    }

    /**
     * Кастомный Intermediate extension для Flow.
     */
    private fun Flow<String>.toUpperCase(): Flow<String> = flow {
        collect {
            emit(it.uppercase(Locale.getDefault()))
        }
    }

    /**
     * Кастомный Terminal extension для Flow.
     */
    private suspend fun Flow<String>.join(): String {
        val sb = StringBuilder()

        collect {
            sb.append(it).append(",")
        }
        return sb.toString()
    }

    /**
     * Просто возвращаем Flow
     */
    private fun flowGenerate() : Flow<String> {
        return flow{
            emit("Result")
        }
    }

    // Lesson 21 Flow операторы channelFlow, flowOn, buffer, produceIn------------------------------

    /**
     * В блоке flow нельзя вызывать emit из разных корутин
     */
    private fun exampleMethod() {
        val flow = flow {
            coroutineScope {
                launch {
                    delay(1000)
                    emit(1)
                }
                launch {
                    delay(1000)
                    emit(2)
                }
            }
        }
    }

    /**
     * channelFlow - позволяет создавать в билдере flow{} несколько корутин и передавать данные в
     * текущую корутину. Корутина закрывается и канал закрывается полсле отправки данных.
     */

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun channelFlowExample() {
        // под капотом
        flow {
            coroutineScope {
                val channel = produce<Int> {
                    launch {
                        delay(1000)
                        send(1)
                    }
                    launch {
                        delay(1000)
                        send(2)
                    }
                    launch {
                        delay(1000)
                        send(3)
                    }
                }
                channel.consumeEach {
                    emit(it)
                }

            }
        }
// пример использования channelFlow
        val flow = channelFlow {
            launch {
                delay(1000)
                send(1)
            }
            launch {
                delay(1000)
                send(2)
            }
            launch {
                delay(1000)
                send(3)
            }
        }
    }

    /**
     * callbackFlow - позволяет подписатья на колбэк и постить данные когда они прийдут. Канал
     * закрывается когда получатель отпишется от Flow. При отписке срабатывает awaitClose, где мы
     * отписываемся от колбэка.
     */

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun callbackFlowExample(): Flow<String> = callbackFlow {
        trySend("Result")
        awaitClose { //close callback }
        }
    }

    /**
     * flowOn - используется для изменения контекста создания данных (val ioFlow = flow.flowOn(Dispatchers.IO))
     * т.е. переключения потока.
     *
     * операторы flow, map выполнятся в IO потоке, onEach в Main
     */
    private fun flowOnExample() {
        val flow = flow {
            emit(1)
        }
            .map {
                // ... IO thread
            }
            .flowOn(Dispatchers.IO)
            .onEach {
                // ... Main thread
            }
            .flowOn(Dispatchers.Main)
    }

    /**
     * buffer - позволяет скапливать данные в буффере, если отправитель шлет данные слишком быстро,
     * а получатель не успевает их обработать.
     */

    private fun bufferExample() {
        val flow = flow {
            emit(3)
        }
            .buffer(5)
            .flowOn(Dispatchers.IO)
    }

    /**
     * produceIn - позволяет конвертировать flow в channel.
     * Получившийся канал являеться Hot.
     */
    @OptIn(FlowPreview::class)
    private fun produceInExample() {
        val channel = flow {
            emit(3)
        }
            .buffer(5)
            .flowOn(Dispatchers.IO)
            .produceIn(scope)
    }

    // Lesson 22 Flow операторы catch, retry, retryWhen --------------------------------------------

    private fun flowGenerateException(): Flow<Int> {
        log("flowGenerateException()")
        return flow {
            val res = 1 / 0
            emit(res)
        }
    }

    /**
     * catch - является аналогом стандартного try-catch. Он перехватит ошибку, чтобы она не ушла в collect.
     * Оператор catch сможет поймать ошибку только из предшествующих ему операторов. Если ошибка
     * возникла в операторе, который в цепочке находится после catch, то она не будет поймана этим catch.
     */
    private fun catchExample() {
        scope.launch {
            flowGenerateException()
                .catch { log("catchExample catch $it") }
                .collect {
                    log("catchExample collect $it")
                }
        }
    }

    /**
     * retry - перезапустит Flow в случае ошибки. Как и оператор catch, он срабатывает только для
     * тех ошибок, которые произошли в предшествующих ему операторах. После retry нужно ставить
     * catch, т.к. после завершения колличества попыток будет краш.
     * Также в блоке кода retry нам доступен метод emit().
     */
    private fun retryExample() {
        log("retryExample()")
        scope.launch {
            flowGenerateException()
                .retry(2) {
                    log("retry")
                    true
                }
                .catch {
                    log(" catch $it")
                }
                .collect {
                    log("collect $it")
                }
        }
    }

    /**
     * retryWhen - работает примерно так же, как и retry. Но он не принимает на вход количество попыток.
     * Вместо этого он дает нам в блок кода номер текущей попытки и ошибку. А нам уже надо
     * решить - возвращать true или false.
     * Также в блоке кода retryWhen нам доступен метод emit().
     */
    private fun retryWhenExample() {
        scope.launch {
            flowGenerateException()
                .retryWhen { cause, attempt ->
                    cause is NetworkErrorException && attempt < 5
                }
                .collect {
                    log("collect $it")
                }
        }
    }

    /**
     * На стороне получателя мы можем остановить Flow вызовам метода cancel() collect{cancel()}.
     * Но этот метод не относится непосредственно к Flow. Это стандартный метод отмены корутины.
     * Если Flow создан не билдером flow, а, например, asFlow, то отмена через метод cancellable()
     * (1..3).asFlow().cancellable()
     */

    // Lesson 23 Практика. Scope, LiveData ---------------------------------------------------------

    private fun launchCoroutineFromViewModel() {
        viewModel.launchCoroutine()
    }

    /**
     * lifecycleScope - привязана к жизненому цыклу Activity.
     * Если создать scope = CoroutineScope(Dispatchers.Main) и не отменить в onDestroy(), то корутина
     * после выхода с экрана будет выполнятся.
     * У lifecycleScope есть билдеры launchWhenCreated, launchWhenStarted и launchWhenResumed
     * для вызова корутин с отслеживанием текущего состояния LifeCycle.
     */
    private fun launchCoroutineFromLifecycleScope() {
        lifecycleScope.launchWhenResumed {
            log("launchWhenResumed")
            while (true) {
                delay(1000)
                log("work")
            }
        }
    }

}