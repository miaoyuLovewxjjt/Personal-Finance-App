# 首页农场场景

当前首页使用参考场景：`app/src/main/res/drawable-nodpi/home_bg.png`（851 × 1847）；木牌文字仍由 App 实时绘制。

交互：邮箱 → 锚定于邮箱的账号下拉菜单，末项为新建账号；start❤ → 原角色卡目录页；两块长木牌 → 分别编辑祝福（1–7 字，保存或取消）；底部心形木牌 → 原设置弹窗。

祝福使用 App 的 PxText 像素字体实时绘制，保存于 Store 外观配置，重启保留，不改变账本数据。默认文案为「日日有小暖」「岁岁有余欢」。

热区以原图像素坐标定义，与图片使用同一缩放/偏移；替换构图时须同步校准。初装沿用 Store 的默认账户，新建账号后自动选中。既有用户保留当前账号。

## 原始生成提示词

Use case: stylized-concept
Asset type: finished portrait background for an interactive Android pixel-farm game title screen, 1024 x 1792 portrait composition.
Create a beautifully art-directed Stardew Valley style original pixel art countryside scene for the Chinese personal finance app 四季记账. Unified crisp deliberate pixel clusters, warm honey wooden objects, cream highlights, lush green grass, blue sky and layered distant mountains. Simple serene composition with generous sky, charming game art, not photorealistic, no phone frame.
Layout: upper 40% sky, fluffy pixel clouds; rolling mountains and a little lake across middle distance. Lower-left a cozy timber cottage with terracotta roof and vegetable patches. At the cottage doorway an unmistakable friendly seated NPC (straw hat, green clothing), writing in an open cream notebook on their lap with a pen, body fully visible, located around x=30% y=65%, large enough to tap in a phone app. Keep NPC free of foreground obstructions.
Upper centered title at x=50% y=23%: EXACT Chinese text "四季记账", four large legible warm cream/gold pixel letters with chunky brown extrusion, decorated with green leaves and little pink/white flowers. No other title.
Lower right: ONE wooden roadside signpost, its entire silhouette within x=56%..90%, y=63%..89%, planted in grass beside a sandy path. Exactly FOUR horizontal planks, straight frontal faces, vertically stacked, separated clearly, mounted on a single post. Top and bottom planks SHORTER, middle two planks LONGER. Top plank around y=67% reads EXACT "start" followed by a red pixel heart. Second plank around y=73% reads EXACT "日日有小暖". Third plank around y=79% reads EXACT "岁岁有余欢". Bottom plank around y=85% displays only a large cream pixel gear icon. Text should be high contrast, legible at small size, lovingly carved game pixel lettering. All four planks remain separate and visible. No additional buttons, no extra signs, no UI panels, no watermark. Keep safe margin around all interactive elements. Scene extends to every edge.

## 像素颗粒调整

Use case: style-transfer
Input image: edit target, current homepage illustration.
Change ONLY pixel-art rendering style, preserving EXACT composition, object positions, all lettering, title, seated writing NPC, and the FOUR wooden sign planks.
The current image is FAR too high-definition / finely textured. Repaint the ENTIRE scene as authentic chunky LOW-RES Stardew Valley / classic 16-bit farming game pixel art. It should look like a native ~240 by 420 pixel game scene enlarged 4x with nearest-neighbor. Large clearly visible SQUARE pixel blocks, hard stair-step edges, limited ~32-48 color palette, 2-3 flat shades per material. Simplified tile-based grass, simple geometric mountains, large flat sky color regions, blocky clouds. Absolutely NO antialiasing, no blur, no fine painterly details, no realistic woodgrain, no tiny dithering, no HD illustration. NPC must look like a simple charming small game sprite with blocky face and clothes, seated writing in notebook at the SAME door location. House is game tile artwork, not detailed illustration.
Keep title "四季记账" at same upper position and same size with chunky pixel lettering and simple blocky leaf/flower decorations.
Keep sign x/y placements unchanged: TOP short "start" plus red pixel heart, middle long "日日有小暖", next long "岁岁有余欢", BOTTOM short with single pixel gear. All words must stay legible, same four planks only. Retain the original portrait aspect ratio and image bounds. The whole scene, including lettering and plants, must share the SAME coarse pixel grid. Colorful, warm, simple, unmistakably mosaic pixel game art.

## 像素字体调整（最终版本）

Use case: text-localization / style-transfer
Edit target: the coarse pixel farm homepage. Change ONLY the typography and lettering style. Preserve scene, NPC, house, plants, mountains, all FOUR wooden board shapes and exact positions.
User explicitly requests true PIXEL FONTS for ALL lettering.
1. Title exact "四季记账": REDRAW as a retro bitmap Chinese PIXEL FONT with square monospaced glyph structure. Blocky equal-thickness strokes, obvious low-resolution stair-step corners, solid cream fill and brown one-pixel outline plus hard offset shadow. NO calligraphic sweeping strokes, no brush style, no smooth edges or bevel gradient. Each Chinese glyph looks drawn on a 24x24 bitmap font grid enlarged. Keep same title bounding box in upper center, retain leaves and flowers.
2. Top board exact "start" and red heart: use a classic small retro 8-bit bitmap font with squared corners and obvious pixel steps (like early farming-game menus). No rounded modern font.
3. Middle boards exact "日日有小暖" and "岁岁有余欢": use a real-looking Chinese 12x12 or 16x16 dot-matrix bitmap game font, thin crisp square block strokes, warm cream high contrast, NO modern sans-serif, NO rounded anti-aliased Chinese lettering. Keep exact wording and board positions.
4. Bottom gear: coarse squared pixel teeth, matching pixel grid.
Preserve portrait composition and positions. All text must remain correct and legible at phone size. No new text, no watermark.
