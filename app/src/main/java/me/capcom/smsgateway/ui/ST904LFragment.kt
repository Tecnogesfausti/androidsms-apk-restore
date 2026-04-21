package me.capcom.smsgateway.ui

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import me.capcom.smsgateway.R
import me.capcom.smsgateway.data.entities.MessageType
import me.capcom.smsgateway.data.entities.MessageWithRecipients
import me.capcom.smsgateway.databinding.FragmentSt904lBinding
import me.capcom.smsgateway.domain.EntitySource
import me.capcom.smsgateway.domain.MessageContent
import me.capcom.smsgateway.modules.incoming.db.IncomingMessage
import me.capcom.smsgateway.modules.incoming.repositories.IncomingMessagesRepository
import me.capcom.smsgateway.modules.messages.MessagesRepository
import me.capcom.smsgateway.modules.messages.MessagesService
import me.capcom.smsgateway.modules.messages.data.Message
import me.capcom.smsgateway.modules.messages.data.SendParams
import me.capcom.smsgateway.modules.messages.data.SendRequest
import org.koin.android.ext.android.inject
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ST904LFragment : Fragment() {
    private var _binding: FragmentSt904lBinding? = null
    private val binding get() = _binding!!

    private val messagesRepo: MessagesRepository by inject()
    private val messagesService: MessagesService by inject()
    private val incomingRepo: IncomingMessagesRepository by inject()
    private val gson = Gson()

    private lateinit var prefs: android.content.SharedPreferences
    private var outgoingMessages: List<MessageWithRecipients> = emptyList()
    private var incomingMessages: List<IncomingMessage> = emptyList()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        prefs = PreferenceManager.getDefaultSharedPreferences(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSt904lBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupCatalog()
        restoreSession()

        binding.editTrackerPhone.doAfterTextChanged {
            prefs.edit {
                putString(PREF_TRACKER_PHONE, it?.toString()?.trim().orEmpty())
            }
            renderTimeline()
        }

        binding.editCommand.doAfterTextChanged {
            prefs.edit {
                putString(PREF_COMMAND_TEXT, it?.toString().orEmpty())
            }
        }

        binding.buttonSend.setOnClickListener { sendCommand() }

        observe(messagesRepo.selectLastWithRecipients(120)) {
            outgoingMessages = it ?: emptyList()
            renderTimeline()
        }

        observe(incomingRepo.selectLast(120)) {
            incomingMessages = it ?: emptyList()
            renderTimeline()
        }

        renderTimeline()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupCatalog() {
        val labels = commandCatalog.map { it.label }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPreset.adapter = adapter
        binding.spinnerPreset.setSelection(loadPresetIndex())
        binding.spinnerPreset.onItemSelectedListener =
            SimpleItemSelectedListener { position ->
                val preset = commandCatalog[position]
                binding.editCommand.setText(preset.template)
                bindPresetDoc(preset)
                prefs.edit {
                    putInt(PREF_PRESET_INDEX, position)
                }
            }
        bindPresetDoc(commandCatalog[binding.spinnerPreset.selectedItemPosition.coerceIn(commandCatalog.indices)])
    }

    private fun restoreSession() {
        binding.editTrackerPhone.setText(prefs.getString(PREF_TRACKER_PHONE, "").orEmpty())
        val command = prefs.getString(PREF_COMMAND_TEXT, null)
        if (!command.isNullOrBlank()) {
            binding.editCommand.setText(command)
        } else {
            val preset = commandCatalog[loadPresetIndex()]
            binding.editCommand.setText(preset.template)
        }
        binding.textSendResult.text = getString(R.string.st904l_ready)
    }

    private fun loadPresetIndex(): Int {
        val index = prefs.getInt(PREF_PRESET_INDEX, DEFAULT_PRESET_INDEX)
        return index.coerceIn(commandCatalog.indices)
    }

    private fun bindPresetDoc(preset: CommandPreset) {
        binding.textPresetTitle.text = preset.label
        binding.textPresetDescription.text = preset.description
        binding.textPresetTemplate.text = getString(R.string.st904l_template_value, preset.template)
        binding.textPresetExample.text = getString(R.string.st904l_example_value, preset.example)
        binding.textPresetNotes.text = getString(R.string.st904l_notes_value, preset.notes)
    }

    private fun sendCommand() {
        val trackerPhone = binding.editTrackerPhone.text?.toString()?.trim().orEmpty()
        val command = binding.editCommand.text?.toString()?.trim().orEmpty()
        if (trackerPhone.isBlank() || command.isBlank()) {
            binding.textSendResult.text = getString(R.string.st904l_missing_phone_or_command)
            return
        }

        val request = SendRequest(
            source = EntitySource.Local,
            message = Message(
                id = UUID.randomUUID().toString(),
                content = MessageContent.Text(command),
                phoneNumbers = listOf(trackerPhone),
                isEncrypted = false,
                createdAt = Date(),
            ),
            params = SendParams(
                withDeliveryReport = true,
                skipPhoneValidation = false,
                simNumber = null,
                validUntil = null,
                priority = me.capcom.smsgateway.data.entities.Message.PRIORITY_EXPEDITED,
            )
        )

        messagesService.enqueueMessage(request)
        binding.textSendResult.text = getString(R.string.st904l_command_sent_local, command)
    }

    private fun renderTimeline() {
        val tracker = normalizePhone(binding.editTrackerPhone.text?.toString())
        if (tracker.isBlank()) {
            renderEmpty(
                binding.outgoingContainer,
                getString(R.string.st904l_set_tracker_hint)
            )
            renderEmpty(
                binding.incomingContainer,
                getString(R.string.st904l_set_tracker_hint)
            )
            return
        }

        val outgoing = outgoingMessages
            .asSequence()
            .filter { msg ->
                msg.recipients.any { normalizePhone(it.phoneNumber) == tracker }
            }
            .mapNotNull { msg ->
                val text = decodeTextMessage(msg) ?: return@mapNotNull null
                OutgoingTimelineItem(
                    id = msg.message.id,
                    createdAt = msg.message.createdAt,
                    state = msg.state.name,
                    text = text,
                    recipients = msg.recipients.joinToString { recipient ->
                        recipient.phoneNumber + ":" + recipient.state.name
                    }
                )
            }
            .take(30)
            .toList()

        val incoming = mergeIncomingParts(
            incomingMessages
                .asSequence()
                .filter { normalizePhone(it.sender) == tracker }
                .take(60)
                .toList()
        ).take(30)

        renderOutgoing(outgoing)
        renderIncoming(incoming)
    }

    private fun renderOutgoing(items: List<OutgoingTimelineItem>) {
        if (items.isEmpty()) {
            renderEmpty(binding.outgoingContainer, getString(R.string.st904l_no_outgoing))
            return
        }

        binding.outgoingContainer.removeAllViews()
        items.forEach { item ->
            val subtitle = getString(
                R.string.st904l_outgoing_meta,
                item.state,
                formatDate(item.createdAt),
                item.recipients,
                item.id
            )
            binding.outgoingContainer.addView(
                buildCard(
                    title = item.text,
                    subtitle = subtitle,
                    body = null
                )
            )
        }
    }

    private fun renderIncoming(items: List<IncomingTimelineItem>) {
        if (items.isEmpty()) {
            renderEmpty(binding.incomingContainer, getString(R.string.st904l_no_incoming))
            return
        }

        binding.incomingContainer.removeAllViews()
        items.forEach { item ->
            val subtitle = getString(
                R.string.st904l_incoming_meta,
                item.sender,
                formatDate(item.createdAt),
                item.id
            ) + if (item.mergedCount > 1) {
                " | " + getString(R.string.st904l_merged_parts, item.mergedCount)
            } else {
                ""
            }

            val body = if (looksLikeRconf(item.text)) {
                formatRconf(item.text)
            } else {
                item.text
            }

            binding.incomingContainer.addView(
                buildCard(
                    title = if (looksLikeRconf(item.text)) {
                        getString(R.string.st904l_rconf_decoded)
                    } else {
                        item.text
                    },
                    subtitle = subtitle,
                    body = if (looksLikeRconf(item.text)) body else null
                )
            )
        }
    }

    private fun buildCard(title: String, subtitle: String, body: String?): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            background = context.getDrawable(android.R.drawable.dialog_holo_light_frame)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }

            addView(TextView(context).apply {
                text = title
                setTypeface(typeface, Typeface.BOLD)
                textSize = 15f
            })

            addView(TextView(context).apply {
                text = subtitle
                textSize = 12f
                alpha = 0.72f
                setPadding(0, 8, 0, 0)
            })

            if (!body.isNullOrBlank()) {
                addView(TextView(context).apply {
                    text = body
                    textSize = 13f
                    setPadding(0, 12, 0, 0)
                    movementMethod = ScrollingMovementMethod()
                    inputType = InputType.TYPE_TEXT_FLAG_MULTI_LINE
                })
            }
        }
    }

    private fun renderEmpty(container: LinearLayout, message: String) {
        container.removeAllViews()
        container.addView(TextView(requireContext()).apply {
            text = message
            alpha = 0.72f
            setPadding(0, 8, 0, 8)
        })
    }

    private fun decodeTextMessage(message: MessageWithRecipients): String? {
        return when (message.message.type) {
            MessageType.Text -> gson.fromJson(
                message.message.content,
                MessageContent.Text::class.java
            ).text

            MessageType.Data -> "[DATA message]"
        }
    }

    private fun normalizePhone(phone: String?): String {
        return phone.orEmpty()
            .replace("\\s+".toRegex(), "")
            .removePrefix("+")
    }

    private fun looksLikeRconf(text: String): Boolean {
        val upper = text.uppercase(Locale.ROOT)
        return upper.contains("ID:") && upper.contains("MODE:") && upper.contains("APN:")
    }

    private fun formatRconf(text: String): String {
        val parts = mutableMapOf<String, String>()
        text.split(",").map { it.trim() }.forEach { chunk ->
            val idx = chunk.indexOf(':')
            if (idx <= 0) return@forEach
            parts[chunk.substring(0, idx).trim().uppercase(Locale.ROOT)] =
                chunk.substring(idx + 1).trim()
        }

        val rows = listOf(
            "ID" to parts["ID"],
            "UP" to parts["UP"],
            "U1" to parts["U1"],
            "U2" to parts["U2"],
            "U3" to parts["U3"],
            "MODE" to parts["MODE"],
            "APN" to parts["APN"],
            "IP" to parts["IP"],
            "GPRS UPLOAD TIME" to parts["GPRS UPLOAD TIME"],
            "TIME ZONE" to parts["TIME ZONE"],
        )

        return buildString {
            rows.forEach { (key, value) ->
                append(key)
                append(": ")
                append(value ?: "-")
                append('\n')
            }
            append('\n')
            append(text)
        }.trim()
    }

    private fun mergeIncomingParts(items: List<IncomingMessage>): List<IncomingTimelineItem> {
        val sorted = items.sortedBy { it.createdAt }
        val merged = mutableListOf<IncomingTimelineItem>()

        sorted.forEach { item ->
            val current = IncomingTimelineItem(
                id = item.id,
                sender = item.sender,
                text = item.contentPreview,
                createdAt = item.createdAt,
                mergedCount = 1
            )

            val last = merged.lastOrNull()
            if (last == null || !shouldMerge(last, current)) {
                merged += current
                return@forEach
            }

            merged[merged.lastIndex] = last.copy(
                id = last.id + "+" + current.id,
                text = joinSmsParts(last.text, current.text),
                createdAt = current.createdAt,
                mergedCount = last.mergedCount + 1
            )
        }

        return merged.sortedByDescending { it.createdAt }
    }

    private fun shouldMerge(previous: IncomingTimelineItem, next: IncomingTimelineItem): Boolean {
        if (normalizePhone(previous.sender) != normalizePhone(next.sender)) return false
        if (kotlin.math.abs(previous.createdAt - next.createdAt) > 120_000L) return false

        val previousKeys = rconfKeyCount(previous.text)
        val nextKeys = rconfKeyCount(next.text)
        val mergedKeys = rconfKeyCount(joinSmsParts(previous.text, next.text))

        return mergedKeys >= 4 && previousKeys < 4 && nextKeys < 4
    }

    private fun joinSmsParts(left: String, right: String): String {
        val first = left.trim()
        val second = right.trim()
        if (first.isBlank()) return second
        if (second.isBlank()) return first
        return if (first.endsWith(",") || first.endsWith(";")) {
            "$first $second"
        } else {
            "$first, $second"
        }
    }

    private fun rconfKeyCount(text: String): Int {
        val upper = text.uppercase(Locale.ROOT)
        val keys = listOf("ID:", "UP:", "U1:", "U2:", "U3:", "MODE:", "APN:", "IP:", "GPRS UPLOAD TIME:", "TIME ZONE:")
        return keys.count { upper.contains(it) }
    }

    private fun formatDate(timestamp: Long): String {
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
    }

    private fun <T> observe(source: LiveData<T>, observer: (T?) -> Unit) {
        source.observe(viewLifecycleOwner) { observer(it) }
    }

    companion object {
        private const val PREF_TRACKER_PHONE = "st904l_phone"
        private const val PREF_COMMAND_TEXT = "st904l_command"
        private const val PREF_PRESET_INDEX = "st904l_preset_index"
        private const val DEFAULT_PRESET_INDEX = 11

        fun newInstance() = ST904LFragment()

        private val commandCatalog = listOf(
            CommandPreset("POS_SMS", "6690000 - SMS tracking", "6690000", "6690000", "Solicita posicion por SMS.", "Comando directo al SIM del tracker."),
            CommandPreset("ADMIN_SET_1", "Admin set #1", "{ADMIN_PHONE}0000 1", "132657901800000 1", "Define numero administrador principal.", "Formato exacto: telefono + password + indice."),
            CommandPreset("VOICE_MONITOR_66", "66 - Voice monitor callback", "66", "66", "El tracker devuelve llamada al admin.", "Solo funciona si ya existe admin configurado."),
            CommandPreset("ADMIN_CANCEL_D101", "D101# - admin cancel", "D101#", "D101#", "Cancela el numero admin.", "La sintaxis puede variar por firmware."),
            CommandPreset("SPEED_SET", "1220000 070 - overspeed set", "1220000 070", "1220000 070", "Alarma de velocidad a 70 km/h.", "Usa tres digitos para velocidad."),
            CommandPreset("SPEED_CANCEL", "1220000 0 - overspeed off", "1220000 0", "1220000 0", "Desactiva alarma de velocidad.", "Debe responder SET OK!."),
            CommandPreset("SHAKE_SET", "1810000T10 - shake alarm set", "1810000T10", "1810000T10", "Activa alarma de vibracion.", "Deja el tracker quieto unos minutos."),
            CommandPreset("SHAKE_CANCEL", "1800000 - shake alarm off", "1800000", "1800000", "Desactiva alarma de vibracion.", "Debe responder SET OK!."),
            CommandPreset("MODE_WORK", "WORK0000 - keep working mode", "WORK0000", "WORK0000", "Modo trabajo continuo.", "Mayor consumo de bateria."),
            CommandPreset("MODE_MOVE", "MOVE0000 - move mode", "MOVE0000", "MOVE0000", "Modo trabajo por movimiento.", "Buen equilibrio entre respuesta y autonomia."),
            CommandPreset("MODE_STANDBY", "STANDBY0000 - standby mode", "STANDBY0000", "STANDBY0000", "Modo standby.", "Despierta por SMS o llamada."),
            CommandPreset("RCONF", "RCONF - read config", "RCONF", "RCONF", "Solicita configuracion actual del equipo.", "Util para validar APN, servidor y modo."),
            CommandPreset("MODE_GPRS", "7100000 - set GPRS mode", "7100000", "7100000", "Cambia a modo GPRS.", "Puede requerir reporte adicional."),
            CommandPreset("MODE_SMS", "7000000 - set SMS mode", "7000000", "7000000", "Cambia a modo SMS.", "Adecuado para uso solo por comandos SMS."),
            CommandPreset("RESET", "RESET - restart tracker", "RESET", "RESET", "Reinicia el tracker.", "Puede quedar offline unos segundos."),
            CommandPreset("APN_SET", "8030000 - set APN", "8030000 {APN}", "8030000 internet", "Configura APN.", "Depende del operador de la SIM."),
            CommandPreset("APN_USER_PASS", "APN user/pass", "8030000 {APN} {APN_USER} {APN_PASS}", "8030000 iot.movistar.es movistar movistar", "APN con usuario y clave.", "Solo si el operador exige autenticacion."),
            CommandPreset("SERVER_SET", "8040000 - server host/port", "8040000 {HOST} {PORT}", "8040000 47.254.77.28 8090", "Configura host y puerto de servidor.", "Usalo para modo plataforma TCP."),
            CommandPreset("TIMEZONE", "8960000 - timezone", "8960000 {TZ}", "8960000 E00", "Configura zona horaria.", "Ejemplos: E00, E01, W03."),
            CommandPreset("CHECK_SIM", "CHECK - SIM/network info", "CHECK", "CHECK", "Consulta estado SIM y red.", "Util para diagnostico sin datos."),
            CommandPreset("FACTORY", "FACTORY - reset defaults", "FACTORY", "FACTORY", "Reset a valores de fabrica.", "Impacto alto: hay que reconfigurar todo.")
        )
    }

    private data class CommandPreset(
        val key: String,
        val label: String,
        val template: String,
        val example: String,
        val description: String,
        val notes: String
    )

    private data class OutgoingTimelineItem(
        val id: String,
        val createdAt: Long,
        val state: String,
        val text: String,
        val recipients: String
    )

    private data class IncomingTimelineItem(
        val id: String,
        val sender: String,
        val text: String,
        val createdAt: Long,
        val mergedCount: Int
    )

    private class SimpleItemSelectedListener(
        private val onSelected: (Int) -> Unit
    ) : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(
            parent: AdapterView<*>?,
            view: View?,
            position: Int,
            id: Long
        ) {
            onSelected(position)
        }

        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    }
}
