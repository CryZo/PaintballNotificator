package com.chaser.paintballnotificator

import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chaser.paintballnotificator.ui.theme.PaintballNotificatorTheme
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.random.Random


class MainActivity : ComponentActivity() {
  private var client: Mqtt3AsyncClient? = null
  private var alarmJob: Job? = null


  @OptIn(ExperimentalMaterial3Api::class, DelicateCoroutinesApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {

      val selectedTeam = rememberSaveable { mutableIntStateOf(1) }
      val teamStatus = remember { mutableStateListOf<Boolean>(false, false) }
      val gameId = rememberSaveable { mutableStateOf("") }
      val connecting = rememberSaveable { mutableStateOf<Boolean>(false) }
      val joinFieldVisible = rememberSaveable { mutableStateOf<Boolean>(false) }
      val alarmTriggered = rememberSaveable { mutableStateOf<Boolean>(false) }

      fun setDefaultData() {
        while (teamStatus.size > 0)
          teamStatus.removeLast()

        teamStatus.add(false)
        teamStatus.add(false)

        selectedTeam.intValue = 1
      }

      fun vibrate() {
        var vibrationEffect: VibrationEffect? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          vibrationEffect = VibrationEffect.createWaveform(longArrayOf(800, 200, 800, 200, 800, 200), intArrayOf(255, 0, 255, 0, 255, 0), -1)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          val v = getSystemService(ComponentActivity.VIBRATOR_MANAGER_SERVICE) as VibratorManager
          v.vibrate(CombinedVibration.createParallel(vibrationEffect!!))
        }
        else {
          @Suppress("DEPRECATION")
          val v = getSystemService(ComponentActivity.VIBRATOR_SERVICE) as Vibrator
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(vibrationEffect!!)
          }
          else {
            @Suppress("DEPRECATION")
            v.vibrate(3000)
          }
        }
      }

      fun checkIfAllTeamsAreReady() {
        alarmJob?.cancel()
        alarmJob = GlobalScope.launch {
          delay(100)
          if (!teamStatus.contains(false) && !alarmTriggered.value) {
            // Alaaaaarm!
            vibrate()
            val mp = MediaPlayer.create(applicationContext, R.raw.alarm)
            mp.setOnCompletionListener { mpi -> mpi.release() }
            mp.start()
          }

          alarmTriggered.value = !teamStatus.contains(false)
        }
      }

      fun publishTeamCount() {
        if (client != null) {
          client!!.publishWith()
            .topic("/paintball/game/${gameId.value}/teams")
            .payload(teamStatus.size.toString().toByteArray())
            .retain(true)
            .send()
        }
      }

      fun publishTeamStatus(index: Int, status: Boolean) {
        if (client != null) {
          client!!.publishWith()
            .topic("/paintball/game/${gameId.value}/team/${index}")
            .payload(status.toString().toByteArray())
            .retain(true)
            .send()
        }
      }

      fun sanitizeSelectedTeam() {
        if (selectedTeam.intValue <= 1)
          selectedTeam.intValue = 1

        if (selectedTeam.intValue > teamStatus.count())
          selectedTeam.intValue = teamStatus.count()
      }

      fun increaseTeam() {
        teamStatus.add(false)
        sanitizeSelectedTeam()
        publishTeamCount()
        checkIfAllTeamsAreReady()
      }
      fun reduceTeam() {
        if (teamStatus.count() <= 2)
          return;

        teamStatus.removeLast()
        sanitizeSelectedTeam()
        publishTeamCount()
        checkIfAllTeamsAreReady()
      }
      fun toggleTeam(index: Int) {
        teamStatus[index] = !teamStatus[index]
        publishTeamStatus(index, teamStatus[index])
        checkIfAllTeamsAreReady()
      }
      fun setTeamCount(count: Int) {
        var c = count
        if (c < 2)
          c = 2

        val oldSize = teamStatus.size

        while (c > teamStatus.size)
          teamStatus.add(false)

        while (c < teamStatus.size)
          teamStatus.removeLast()

        sanitizeSelectedTeam()

        if (oldSize != teamStatus.size)
          publishTeamCount()

        checkIfAllTeamsAreReady()
      }

      fun decreaseSelectedTeam() {
        if (selectedTeam.intValue <= 1)
          return;

        selectedTeam.intValue--;
      }
      fun increaseSelectedTeam() {
        if (selectedTeam.intValue >= teamStatus.count())
          return;

        selectedTeam.intValue++;
      }

      fun setGameId(id: String) {
        gameId.value = id;
        connecting.value = true

        if (client != null)
          client?.disconnect()

        setDefaultData()

        client = MqttClient.builder()
          .useMqttVersion3()
          .identifier("paintball-notificator-${UUID.randomUUID()}")
          .serverHost("broker.hivemq.com")
          .serverPort(1883)
          .buildAsync()

        client!!
          .connectWith()
          .send()
          .whenComplete { _, throwable ->
            if (throwable != null) {
              // handle failure
            }
            else {
              // setup subscribes or start publishing
              connecting.value = false

              client!!.subscribeWith()
                .topicFilter("/paintball/game/${gameId.value}/teams")
                .qos(MqttQos.EXACTLY_ONCE)
                .callback { publish ->
                  try {
                    val data = String(publish.payloadAsBytes, StandardCharsets.UTF_8)
                    setTeamCount(data.toInt())
                  }
                  catch (_: Exception) {

                  }
                }
                .send()

              client!!.subscribeWith()
                .topicFilter("/paintball/game/${gameId.value}/team/#")
                .qos(MqttQos.EXACTLY_ONCE)
                .callback { publish ->
                  try {
                    val index = publish.topic.toString().split("/team/")[1].toInt();
                    val value = String(publish.payloadAsBytes, StandardCharsets.UTF_8) == "true"

                    if (teamStatus.size > index) {
                      teamStatus[index] = value
                    }

                    checkIfAllTeamsAreReady()
                  }
                  catch (_: Exception) {

                  }
                }
                .send()
            }
          }
      }

      fun setRandomGameId() {
        setGameId(Random.nextInt(100000, 999999).toString())
      }

      fun reconnect() {
        if (gameId.value != "")
          setGameId(gameId.value)
      }



      @Composable
      fun TeamStatus(name: String, status: Boolean, modifier: Modifier = Modifier) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = modifier
            .height(128.dp)
            .defaultMinSize(128.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (status) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
        ) {
          Text(
            text = name,
            color = if (status) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary
          )
        }
      }

      @Composable
      fun TeamStatusRow(modifier: Modifier = Modifier) {
        val columns = floor((LocalConfiguration.current.screenWidthDp - 24).toFloat() / 128F).toInt()
        val rows = ceil(teamStatus.size.toFloat() / columns.toFloat()).toInt()
        val height = rows * 133 + (rows-1) * 10

        Row(
          modifier = modifier
            .fillMaxWidth()
            .padding(5.dp)
        ) {
          LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 128.dp),
            userScrollEnabled = false,
            modifier = Modifier
              .weight(1F)
              .height(height.dp)
          ) {
            items(teamStatus.size) { index ->
              TeamStatus((index + 1).toString(), teamStatus[index],
                Modifier
//            .fillMaxWidth()
                  .weight(1f)
                  .padding(5.dp)
              )
            }
          }

          Column(
            modifier = Modifier
//        .weight(1F)
              .width(24.dp)
              .height(138.dp),
            verticalArrangement = Arrangement.SpaceEvenly
          ) {
            IconButton(onClick = { increaseTeam() }) {
              Icon(Icons.Rounded.Add,
                stringResource(R.string.add_team), tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { reduceTeam() }) {
              Icon(Icons.Rounded.Clear,
                stringResource(R.string.remove_team), tint = MaterialTheme.colorScheme.primary)
            }
          }
        }
      }

      @Composable
      fun TeamSelector(modifier: Modifier = Modifier) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = modifier
            .padding(5.dp)
        ) {
          IconButton(onClick = { decreaseSelectedTeam() }) {
            Icon(Icons.Rounded.KeyboardArrowLeft,
              stringResource(R.string.decrease_selected_team), tint = MaterialTheme.colorScheme.onPrimary)
          }
          Text(
            text = "Team: ${selectedTeam.intValue}",
          )
          IconButton(onClick = { increaseSelectedTeam() }) {
            Icon(Icons.Rounded.KeyboardArrowRight,
              stringResource(R.string.increase_selected_team), tint = MaterialTheme.colorScheme.onPrimary)
          }
        }
      }

      @Composable
      fun GameSelector(modifier: Modifier = Modifier) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = modifier
        ) {
          Text(
            text = stringResource(R.string.match_id, gameId.value),
            color = MaterialTheme.colorScheme.onPrimary
          )
          IconButton(onClick = { setRandomGameId() }) {
            Icon(Icons.Rounded.Refresh, stringResource(R.string.generate_new_match), tint = MaterialTheme.colorScheme.onPrimary)
          }
        }
      }

      @Composable
      fun ShareButton(modifier: Modifier = Modifier) {
        IconButton(modifier = modifier, onClick = {
          val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "paintball://${gameId.value}")
            putExtra(Intent.EXTRA_TITLE, getString(R.string.paintball_match_no, gameId.value))
            type = "text/plain"
          }

          val shareIntent = Intent.createChooser(sendIntent, null)
          startActivity(shareIntent)

        }) {
          Icon(Icons.Rounded.Share, stringResource(R.string.share_match_id), tint = MaterialTheme.colorScheme.onPrimary)
        }
      }

      @Composable
      fun TeamReadyToggle(modifier: Modifier = Modifier) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = modifier
            .padding(horizontal = 10.dp)
            .height(256.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (teamStatus[selectedTeam.intValue - 1]) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
            .clickable {
              toggleTeam(selectedTeam.intValue - 1)
            }
        ) {
          Text(
            text = if (teamStatus[selectedTeam.intValue-1]) stringResource(R.string.is_ready) else stringResource(R.string.ask_ready),
            color = if (teamStatus[selectedTeam.intValue - 1]) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary
          )
        }
      }

      @Composable
      fun Welcome(modifier: Modifier = Modifier) {
        Column(
          verticalArrangement = Arrangement.Center,
          modifier = modifier
            .padding(10.dp)
            .fillMaxHeight()
            .fillMaxWidth()
        ) {
          Column(
            verticalArrangement = Arrangement.spacedBy(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
              .fillMaxWidth()
          ) {
            Text(text = stringResource(R.string.welcome), style = MaterialTheme.typography.headlineLarge)
            Text(text = stringResource(R.string.welcome_paragraph), textAlign = TextAlign.Center)
            if (joinFieldVisible.value) {
              Row (horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val id = rememberSaveable { mutableStateOf("") }
                val focusRequester = remember { FocusRequester() }

                OutlinedTextField(
                  modifier = Modifier
                    .focusRequester(focusRequester)
                    .fillMaxWidth(),
                  value = id.value,
                  onValueChange = { newText ->
                    id.value = newText.trim()
                  },
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, autoCorrect = false, imeAction = ImeAction.Go),
                  keyboardActions = KeyboardActions(
                    onGo = { setGameId(id.value) }
                  ),
                  singleLine = true,
                  label = {
                    Text(stringResource(R.string.match_id_input_label))
                  }
                )

                LaunchedEffect(Unit) {
                  focusRequester.requestFocus()
                }
              }
            }
            else {
              Row (horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { joinFieldVisible.value = true }) {
                  Text(text = stringResource(R.string.join))
                }
                Button(onClick = { setRandomGameId() }) {
                  Text(text = stringResource(R.string.create_new))
                }
              }
            }
          }
        }
      }

      @Composable
      fun ShowReadyBanner(modifier: Modifier = Modifier) {
        Card(modifier = modifier
          .fillMaxWidth()
          .padding(top = 10.dp, start = 10.dp, end = 10.dp)) {
          Row (horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.the_match_has_started), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp))
          }
        }
      }


      LaunchedEffect(gameId) {
//      if (teamStatus.value == null || teamStatus.size == 0)
//        setDefaultData()

        try {
          if (intent.dataString != null && intent.dataString != "")
            setGameId(intent.dataString!!.split("paintball://")[1])
        }
        catch (_: Exception) {

        }

//      Reconnect just in case
        reconnect()
      }

      PaintballNotificatorTheme {
        // A surface container using the 'background' color from the theme
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          Scaffold(
            topBar = {
              TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                title = {
                  TeamSelector()
                },
                actions = {
                  if (connecting.value) {
                    CircularProgressIndicator(
//                      modifier = Modifier.width(64.dp),
                      color = MaterialTheme.colorScheme.onPrimary,
                      trackColor = MaterialTheme.colorScheme.primary,
                    )
                  }
                  else {
                    Row (verticalAlignment = Alignment.CenterVertically) {
                      GameSelector()
                      ShareButton()
                    }
                  }
                }
              )
            },
          ) { innerPadding ->
            if (gameId.value != "") {
              LazyColumn(
                modifier = Modifier
                  .padding(innerPadding)
              ) {
                item {
                  if (!teamStatus.contains(false)) {
                    ShowReadyBanner()
                  }

                  TeamStatusRow()
                  TeamReadyToggle()
                }
              }
            } else {
              Welcome()
            }
          }
        }
      }
    }
  }
}
