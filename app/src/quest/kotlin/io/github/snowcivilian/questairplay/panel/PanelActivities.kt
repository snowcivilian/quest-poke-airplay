package io.github.snowcivilian.questairplay.panel

import io.github.jqssun.airplay.MainActivity

// One activity per panel aspect ratio; sizes come from <layout> in src/quest/AndroidManifest.xml.
// MainActivity itself is the 16:9 panel. See PanelSwitcher.
class Panel4x3Activity : MainActivity()
class Panel3x2Activity : MainActivity()
class Panel10x9Activity : MainActivity()
class Panel8x7Activity : MainActivity()
class Panel2x3Activity : MainActivity()
