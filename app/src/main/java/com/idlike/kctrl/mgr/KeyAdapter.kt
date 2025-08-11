package com.idlike.kctrl.mgr

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class KeyAdapter(
    private val keyList: List<KeyItem>,
    private val onItemAction: (KeyItem, String) -> Unit
) : RecyclerView.Adapter<KeyAdapter.KeyViewHolder>() {

    private fun getKeyNameFromCode(keyCode: Int): String {
        return when (keyCode) {
            1 -> "ESC"
            2 -> "1"
            3 -> "2"
            4 -> "3"
            5 -> "4"
            6 -> "5"
            7 -> "6"
            8 -> "7"
            9 -> "8"
            10 -> "9"
            11 -> "0"
            12 -> "-"
            13 -> "="
            14 -> "BACKSPACE"
            15 -> "TAB"
            16 -> "Q"
            17 -> "W"
            18 -> "E"
            19 -> "R"
            20 -> "T"
            21 -> "Y"
            22 -> "U"
            23 -> "I"
            24 -> "O"
            25 -> "P"
            26 -> "["
            27 -> "]"
            28 -> "ENTER"
            29 -> "LEFT_CTRL"
            30 -> "A"
            31 -> "S"
            32 -> "D"
            33 -> "F"
            34 -> "G"
            35 -> "H"
            36 -> "J"
            37 -> "K"
            38 -> "L"
            39 -> ";"
            40 -> "'"
            41 -> "`"
            42 -> "LEFT_SHIFT"
            43 -> "\\"
            44 -> "Z"
            45 -> "X"
            46 -> "C"
            47 -> "V"
            48 -> "B"
            49 -> "N"
            50 -> "M"
            51 -> ","
            52 -> "."
            53 -> "/"
            54 -> "RIGHT_SHIFT"
            55 -> "KP_ASTERISK"
            56 -> "LEFT_ALT"
            57 -> "SPACE"
            58 -> "CAPS_LOCK"
            59 -> "F1"
            60 -> "F2"
            61 -> "F3"
            62 -> "F4"
            63 -> "F5"
            64 -> "F6"
            65 -> "F7"
            66 -> "F8"
            67 -> "F9"
            68 -> "F10"
            69 -> "NUM_LOCK"
            70 -> "SCROLL_LOCK"
            71 -> "KP_7"
            72 -> "KP_8"
            73 -> "KP_9"
            74 -> "KP_MINUS"
            75 -> "KP_4"
            76 -> "KP_5"
            77 -> "KP_6"
            78 -> "KP_PLUS"
            79 -> "KP_1"
            80 -> "KP_2"
            81 -> "KP_3"
            82 -> "KP_0"
            83 -> "KP_DOT"
            87 -> "F11"
            88 -> "F12"
            96 -> "KP_ENTER"
            97 -> "RIGHT_CTRL"
            98 -> "KP_SLASH"
            99 -> "SYSRQ"
            100 -> "RIGHT_ALT"
            102 -> "HOME"
            103 -> "UP"
            104 -> "PAGE_UP"
            105 -> "LEFT"
            106 -> "RIGHT"
            107 -> "END"
            108 -> "DOWN"
            109 -> "PAGE_DOWN"
            110 -> "INSERT"
            111 -> "DELETE"
            113 -> "MUTE"
            114 -> "VOLUME_DOWN"
            115 -> "VOLUME_UP"
            116 -> "POWER"
            117 -> "KP_EQUAL"
            119 -> "PAUSE"
            125 -> "LEFT_META"
            126 -> "RIGHT_META"
            127 -> "COMPOSE"
            128 -> "STOP"
            129 -> "AGAIN"
            130 -> "PROPS"
            131 -> "UNDO"
            132 -> "FRONT"
            133 -> "COPY"
            134 -> "OPEN"
            135 -> "PASTE"
            136 -> "FIND"
            137 -> "CUT"
            138 -> "HELP"
            139 -> "MENU"
            140 -> "CALC"
            141 -> "SETUP"
            142 -> "SLEEP"
            143 -> "WAKEUP"
            144 -> "FILE"
            145 -> "SEND_FILE"
            146 -> "DELETE_FILE"
            147 -> "XFER"
            148 -> "PROG1"
            149 -> "PROG2"
            150 -> "WWW"
            151 -> "MSDOS"
            152 -> "COFFEE"
            153 -> "ROTATE_DISPLAY"
            154 -> "CYCLE_WINDOWS"
            155 -> "MAIL"
            156 -> "BOOKMARKS"
            157 -> "COMPUTER"
            158 -> "BACK"
            159 -> "FORWARD"
            160 -> "CLOSE_CD"
            161 -> "EJECT_CD"
            162 -> "EJECT_CLOSE_CD"
            163 -> "NEXT_SONG"
            164 -> "PLAY_PAUSE"
            165 -> "PREVIOUS_SONG"
            166 -> "STOP_CD"
            167 -> "RECORD"
            168 -> "REWIND"
            169 -> "PHONE"
            170 -> "ISO"
            171 -> "CONFIG"
            172 -> "HOMEPAGE"
            173 -> "REFRESH"
            174 -> "EXIT"
            175 -> "MOVE"
            176 -> "EDIT"
            177 -> "SCROLL_UP"
            178 -> "SCROLL_DOWN"
            179 -> "KP_LEFT_PAREN"
            180 -> "KP_RIGHT_PAREN"
            181 -> "NEW"
            182 -> "REDO"
            183 -> "F13"
            184 -> "F14"
            185 -> "F15"
            186 -> "F16"
            187 -> "F17"
            188 -> "F18"
            189 -> "F19"
            190 -> "F20"
            191 -> "F21"
            192 -> "F22"
            193 -> "F23"
            194 -> "F24"
            200 -> "PLAY_CD"
            201 -> "PAUSE_CD"
            202 -> "PROG3"
            203 -> "PROG4"
            204 -> "DASHBOARD"
            205 -> "SUSPEND"
            206 -> "CLOSE"
            207 -> "PLAY"
            208 -> "FAST_FORWARD"
            209 -> "BASS_BOOST"
            210 -> "PRINT"
            211 -> "HP"
            212 -> "CAMERA"
            213 -> "SOUND"
            214 -> "QUESTION"
            215 -> "EMAIL"
            216 -> "CHAT"
            217 -> "SEARCH"
            218 -> "CONNECT"
            219 -> "FINANCE"
            220 -> "SPORT"
            221 -> "SHOP"
            222 -> "ALT_ERASE"
            223 -> "CANCEL"
            224 -> "BRIGHTNESS_DOWN"
            225 -> "BRIGHTNESS_UP"
            226 -> "MEDIA"
            227 -> "SWITCH_VIDEO_MODE"
            228 -> "KBDILLUM_TOGGLE"
            229 -> "KBDILLUM_DOWN"
            230 -> "KBDILLUM_UP"
            231 -> "SEND"
            232 -> "REPLY"
            233 -> "FORWARD_MAIL"
            234 -> "SAVE"
            235 -> "DOCUMENTS"
            236 -> "BATTERY"
            237 -> "BLUETOOTH"
            238 -> "WLAN"
            239 -> "UWB"
            240 -> "UNKNOWN"
            241 -> "VIDEO_NEXT"
            242 -> "VIDEO_PREV"
            243 -> "BRIGHTNESS_CYCLE"
            244 -> "BRIGHTNESS_AUTO"
            245 -> "DISPLAY_OFF"
            246 -> "WWAN"
            247 -> "RFKILL"
            248 -> "MICMUTE"
            else -> "KEY_$keyCode"
        }
    }

    class KeyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvKeyName: TextView = itemView.findViewById(R.id.tv_key_name)
        val tvKeyCode: TextView = itemView.findViewById(R.id.tv_key_code)
        val tvScriptCount: TextView = itemView.findViewById(R.id.tv_script_count)
        val btnEdit: ImageButton = itemView.findViewById(R.id.btn_edit)
        val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KeyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_key, parent, false)
        return KeyViewHolder(view)
    }

    override fun onBindViewHolder(holder: KeyViewHolder, position: Int) {
        val key = keyList[position]
        
        holder.tvKeyName.text = getKeyNameFromCode(key.code)
        holder.tvKeyCode.text = "代码: ${key.code}"
        
        // 计算配置的脚本数量
        val scriptCount = listOf(
            key.clickScript,
            key.doubleClickScript,
            key.shortPressScript,
            key.longPressScript
        ).count { it.isNotEmpty() }
        
        holder.tvScriptCount.text = "已配置 $scriptCount 个操作"
        
        holder.btnEdit.setOnClickListener {
            onItemAction(key, "edit")
        }
        
        holder.btnDelete.setOnClickListener {
            onItemAction(key, "delete")
        }
    }

    override fun getItemCount(): Int = keyList.size
}