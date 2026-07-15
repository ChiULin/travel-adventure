MERGE INTO cities (id, name, code, intro, unlock_order, boss_name, boss_power) KEY(id) VALUES
  (1, '台北', '北', '從城市天際線、歷史廣場與老街展開旅程。', 1, '台北守護者', 12),
  (2, '台中', '中', '探索濕地、彩繪眷村與現代建築。', 2, '中城策士', 16),
  (3, '台南', '南', '走訪古都街區與歷史地標，收集府城記憶。', 3, '古城守護者', 18),
  (4, '高雄', '高', '欣賞港灣景色、藝術特區與海岸景點。', 4, '港都哨兵', 20),
  (5, '花蓮', '花', '挑戰山海交織的路線，認識原住民文化。', 5, '山海旅人', 24),
  (6, '澎湖', '澎', '收集海島地景、跨海大橋與夏季花火。', 6, '海島守衛', 28);

MERGE INTO scenes (id, name, type, description, image_url, rarity, exp_reward, coin_reward, is_hidden, city_id) KEY(id) VALUES
  (1, '中正紀念堂', '歷史建築', '漫步自由廣場，欣賞白牆藍瓦的紀念建築與臺北城市風景。', '/images/landmarks/chiang-kai-shek-memorial.webp', 2, 125, 105, false, 1),
  (2, '淡水老街', '河岸老街', '沿著淡水河散步，收集夕陽、老街小吃與北台灣河港風情。', '/images/landmarks/tamsui-old-street.webp', 1, 95, 90, false, 1),
  (3, '台北 101', '城市地標', '登上臺灣代表性的摩天大樓，從高空俯瞰臺北盆地與城市天際線。', '/images/landmarks/taipei-101.webp', 2, 130, 110, false, 1),
  (4, '臺中國家歌劇院', '現代建築', '走進由曲牆與洞穴空間展開的現代建築，感受藝術與城市交會。', '/images/landmarks/taichung-theater.webp', 2, 140, 120, false, 2),
  (5, '高美濕地', '自然景觀', '沿著木棧道走向夕陽與風車，觀察潮間帶生態與壯闊海景。', '/images/landmarks/gaomei-wetlands.webp', 2, 135, 115, false, 2),
  (6, '彩虹眷村', '藝術聚落', '走進色彩繽紛的彩繪聚落，收藏台中最活潑的城市記憶。', '/images/landmarks/rainbow-village.webp', 1, 95, 90, false, 2),
  (7, '赤崁樓', '歷史古蹟', '探訪臺南古城核心，感受亭閣、石碑與廟埕交織出的歷史層次。', '/images/landmarks/chihkan-tower.webp', 2, 140, 120, false, 3),
  (8, '台南神農街', '歷史街區', '穿梭老屋、燈籠與文創小店，收集府城巷弄裡的慢步調。', '/images/landmarks/shennong-street.webp', 1, 100, 95, false, 3),
  (9, '安平古堡', '歷史古蹟', '穿梭紅磚城牆與老樹之間，閱讀臺南數百年的海港歷史。', '/images/landmarks/anping-fort.webp', 2, 145, 125, false, 3),
  (10, '旗津', '海港風景', '搭渡輪前往高雄港邊沙洲，漫步海岸、燈塔與老街，感受南方海風。', '/images/landmarks/cijin.webp', 3, 155, 135, false, 4),
  (11, '駁二藝術特區', '藝術空間', '走進由舊倉庫改造的創意街區，探索展覽、裝置藝術與港邊風景。', '/images/landmarks/pier-2-art-center.webp', 2, 145, 125, false, 4),
  (12, '蓮池潭', '湖畔地標', '造訪高雄左營的經典湖景，收藏龍虎塔與廟宇倒影。', '/images/landmarks/dragon-tiger-pagodas.webp', 2, 150, 125, false, 4),
  (13, '阿美文化村', '原住民文化', '走進花蓮阿美族文化場域，認識木雕、圖騰、歌舞與山海生活記憶。', '/images/landmarks/amis-cultural-village.webp', 3, 165, 140, false, 5),
  (14, '七星潭', '海岸風景', '欣賞遼闊海景與礫石海灘，穩定累積山海旅程獎勵。', '/images/landmarks/qixingtan-beach.webp', 2, 135, 120, false, 5),
  (15, '太魯閣國家公園', '山岳風景', '走入花蓮峽谷與山林步道，完成高價值的山海探索。', '/images/landmarks/taroko-gorge.webp', 3, 165, 130, false, 5),
  (16, '雙心石滬', '海島地景', '在潮汐之間尋找澎湖最浪漫的石滬線條，收藏海島經典風景。', '/images/landmarks/twin-hearts-stone-weir.webp', 3, 165, 145, false, 6),
  (17, '澎湖跨海大橋', '海島地標', '跨越碧藍海面與島嶼道路，完成澎湖代表性的海風路線。', '/images/landmarks/penghu-great-bridge.webp', 2, 145, 125, false, 6),
  (18, '澎湖花火節', '節慶景觀', '在夏夜煙火與海灣燈影中，收集澎湖最閃耀的節慶記憶。', '/images/landmarks/penghu-fireworks-festival.webp', 2, 140, 130, false, 6);
