package com.example.coroutinestartandroid

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.NonCancellable.isActive
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel : ViewModel() {

    private val TAG = "TAG ${this.javaClass.simpleName}"
    private var formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCleared() {
        super.onCleared()
        log("onCleared")
        scope.cancel()
    }

    /**
     * viewModelScope - привязана к жизненому цыклу ViewModel.
     * Если создать scope = CoroutineScope(Dispatchers.Main) и не отменить в onCleared(), то корутина
     * после выхода с экрана будет выполнятся.
     */
    fun launchCoroutine() {
        log("launch")
        viewModelScope.launch {
            while (true) {
                delay(1000)
                log("work")
            }
        }
    }

    /**
     * Это корректный запуск кода
     * Код внутри корутины выполняется в main потоке. Запрос данных внутри withContext будет
     * выполнен в IO потоке. Main поток в это время не будет заблокирован.
     */
    fun fetchData() {
        viewModelScope.launch {
            val response = withContext(Dispatchers.IO) {
                // launch useCase
            }
        }
    }

    private fun log(text: String) {
        Log.d(TAG, "${formatter.format(Date())} $text [${Thread.currentThread().name}]")
    }

    // Lesson 28 SharedFlow и StateFlow ------------------------------------------------------------
    /**
     * SharedFlow - hot Flow
     * SharedFlow - потокобезопасен.
     * У SharedFlow есть два отдельных интерфейса: MutableSharedFlow - для отправителей, SharedFlow - для получателей.
     * Методом emit() отправляем данные, методом collect() подписываемся на получение данных.
     * Для отмены подписки на получение данных необходимо отменить корутину в которой запущен SharedFlow.
     * Конвертация MutableSharedFlow в SharedFlow через метод "asSharedFlow()"
     *
     * Каждый раз когда в Flow приходян новый данные отрабатывает код в методе collect() и если этот
     * блок кода не успел выполниться для последних данных, то получение новых данных будет заблокировано!!!
     * Именно по этому emit() является suspend, который может приостановать выполнение кода, пока
     * получатель не будет готов принимать данные. Чтобы смягчить ситуацию можно включить буффер.
     *
     * Параметры конструктора MutableSharedFlow
     * replay - включает буффер указаного размера и кэш. Буфер может быть пустым, если все получатели
     * достаточно быстрые, но кэш при этом будет полным.  Буфер нужен, чтобы компенсировать работу
     * медленных получателей. А кэш нужен, чтобы новые получатели сразу смогли получить несколько пришедших ранее данных.
     * extraBufferCapacity - служит для увеличения размера буфера, заданного параметром replay.
     * Если мы используем и replay и extraBufferCapacity: val shareFlow = MutableSharedFlow<Event>(replay = 3, extraBufferCapacity = 4)
     * то размер буфера будет равен сумме значений этих параметров: 3+4=7. Но размер кэша будет равен 3.
     * onBufferOverflow - задает поведение ShareFlow, когда отправитель шлет данные, но буффер уже заполнен.
     * Режимы:
     * SUSPEND - метод emit на стороне отправителя будет приостанавливать корутину, пока не появятся
     * свободные слоты в буфере. Т.е. пока самый медленный получатель не получит значение, после чего
     * оно будет удалено из буфера. При этом режиме даже быстрым получателям придется ждать новых данных.
     * Данные просто не смогут пройти минуя буфер. Этот режим используется по умолчанию.
     * Избежать приостановки можно не suspend методом tryEmit().
     * DROP_OLDEST - метод emit будет удалять из заполненного буфера наиболее старые элементы и добавлять
     * новые. Плюс в том, что методу emit больше не придется ждать. Отправка будет мгновенной.
     * С другой стороны, до медленных получателей дойдут не все отправленные данные.
     * DROP_LATEST - метод emit не будет отправлять новые значения, если буфер заполнен. Метод emit
     * в этом случае также не приостанавливает корутину и отрабатывает быстро. Но все получатели будут
     * пропускать новые данные, если особо медленные получатели все обрабатывают старые данные.
     *
     * replayCache - посмотреть содержимое кэша.
     * resetReplayCache() - очистить кэш.
     * subscriptionCount - можно следить за колличество подписчиков см. subscriptionCountExample()
     * shareIn - из Flow делает ShareFlow.
     * flow.shareIn(scope = viewModelScope, started = SharingStarted.Lazily, replay = 3)
     * параметры метода shareIn:
     * scope - scope для запуска корутины, которая запускает flow.
     * started - режимы запуска SharedFlow(Eagerly, Lazily, WhileSubscribed)
     * Eagerly - работа в Flow стартует сразу при создании SharedFlow, даже если еще нет подписчиков.
     * В этом случае данные пойдут в никуда (и в кэш). Flow будет работать, пока не отменим scope.
     * Lazily - стартует при появлении первого подписчика. Flow будет работать, пока не отменим scope.
     * WhileSubscribed - стартует при появлении первого подписчика. При уходе последнего подписчика - останавливается.
     * Т.е. отменяется подкапотная корутина, в которой работал оригинальный Flow.
     * У WhileSubscribed есть свои подпараметры:
     * stopTimeoutMillis - сколько времени ждать до остановки работы с момента ухода последнего подписчика
     * replayExpirationMillis - как долго живет replay кэш после остановки работы
     * replay - имеет то же значение, что и у SharedFlow. Задает размер буфера и кэша.
     *
     * StateFlow  - это частный случай SharedFlow.
     * val shared = MutableSharedFlow(replay = 1,onBufferOverflow = BufferOverflow.DROP_OLDEST) -
     * StateFlow созданный через SharedFlow.
     * StateFlow всегда будет хранить одно(последнее значение), которое будет получать каждый новый
     * подписчик.Новые значения всегда будут заменять в буфере старые.
     * StateFlow.value - получить последнее заначение.
     * stateIn - превращает Flow в StateFlow.
     */

    private val mutableSharedFlow = MutableSharedFlow<String>()

    private fun subscriptionCountExample() {
        mutableSharedFlow.subscriptionCount
            .map { count -> count > 0 } // map count into active/inactive flag
            .distinctUntilChanged() // only react to true<->false changes
            .onEach { isActive -> // configure an action
                if (isActive) {
                    //onActive()
                } else {
                    //onInactive()
                }
            }
            .launchIn(scope) // launch it
    }
}