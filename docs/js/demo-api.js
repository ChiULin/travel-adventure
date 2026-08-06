const DEMO_STORAGE_KEY = "travelAdventureDemoState";

const createDemoState = () => ({
  token: "demo-jwt-token",
  player: {
    id: 1,
    username: "Demo旅人",
    level: 1,
    exp: 0,
    coins: 200,
    title: "旅行新手",
    bossPoints: 0
  },
  currentCityId: 1,
  checkins: [],
  completedBosses: [],
  unlockedCities: [1],
  achievements: [],
  tutorialCompleted: false
});

let demoState = loadDemoState();

function loadDemoState() {
  try {
    const saved = localStorage.getItem(DEMO_STORAGE_KEY);
    return saved ? JSON.parse(saved) : createDemoState();
  } catch {
    return createDemoState();
  }
}

function saveDemoState() {
  localStorage.setItem(DEMO_STORAGE_KEY, JSON.stringify(demoState));
}

function resetDemoState() {
  demoState = createDemoState();
  saveDemoState();
  location.reload();
}

const demoCities = [
  {
    id: 1,
    code: "TAIPEI",
    name: "台北",
    unlockOrder: 1,
    bossName: "台北守護者",
    scenes: [
      {
        id: 1,
        name: "台北101",
        imageUrl: "./images/landmarks/taipei-101.webp",
        description: "代表臺北現代城市發展的重要地標。",
        expReward: 60,
        coinReward: 80
      },
      {
        id: 2,
        name: "國立故宮博物院",
        imageUrl: "./images/landmarks/national-palace-museum.png",
        description: "收藏豐富中華文化藝術典藏的博物館。",
        expReward: 50,
        coinReward: 70
      },
      {
        id: 3,
        name: "西門町",
        imageUrl: "./images/landmarks/ximending.png",
        description: "融合流行文化、電影與街頭潮流的商圈。",
        expReward: 40,
        coinReward: 60
      }
    ]
  },
  {
    id: 2,
    code: "TAICHUNG",
    name: "台中",
    unlockOrder: 2,
    bossName: "台中守護者",
    scenes: [
      {
        id: 4,
        name: "高美濕地",
        imageUrl: "./images/landmarks/gaomei-wetlands.webp",
        description: "以夕陽、潮間帶與風力發電景觀聞名。",
        expReward: 60,
        coinReward: 80
      },
      {
        id: 5,
        name: "臺中國家歌劇院",
        imageUrl: "./images/landmarks/taichung-theater.webp",
        description: "以曲牆與洞窟式空間聞名的現代建築。",
        expReward: 50,
        coinReward: 70
      },
      {
        id: 6,
        name: "彩虹眷村",
        imageUrl: "./images/landmarks/rainbow-village.webp",
        description: "以鮮豔彩繪形成的特色文化景點。",
        expReward: 40,
        coinReward: 60
      }
    ]
  },
  {
    id: 3,
    code: "TAINAN",
    name: "台南",
    unlockOrder: 3,
    bossName: "台南守護者",
    scenes: []
  },
  {
    id: 4,
    code: "KAOHSIUNG",
    name: "高雄",
    unlockOrder: 4,
    bossName: "高雄守護者",
    scenes: []
  },
  {
    id: 5,
    code: "HUALIEN",
    name: "花蓮",
    unlockOrder: 5,
    bossName: "花蓮守護者",
    scenes: []
  },
  {
    id: 6,
    code: "PENGHU",
    name: "澎湖",
    unlockOrder: 6,
    bossName: "澎湖守護者",
    scenes: []
  }
];

function demoLogin(username) {
  demoState.player.username = username || "Demo旅人";
  demoState.token = "demo-jwt-token";
  saveDemoState();

  return {
    success: true,
    message: "Demo 登入成功",
    data: {
      userId: demoState.player.id,
      username: demoState.player.username,
      token: demoState.token
    }
  };
}

function demoCheckin(sceneId) {
  const scene = demoCities
    .flatMap(city => city.scenes)
    .find(item => item.id === Number(sceneId));

  if (!scene) {
    return {
      success: false,
      message: "找不到景點",
      data: null
    };
  }

  if (!demoState.checkins.includes(scene.id)) {
    demoState.checkins.push(scene.id);
    demoState.player.exp += scene.expReward;
    demoState.player.coins += scene.coinReward;

    demoState.player.level =
      Math.floor(demoState.player.exp / 220) + 1;

    if (demoState.player.level >= 4) {
      demoState.player.title = "City Explorer";
    }

    saveDemoState();
  }

  return {
    success: true,
    message: "景點挑戰完成",
    data: {
      correct: true,
      checkinCreated: true,
      expReward: scene.expReward,
      coinReward: scene.coinReward
    }
  };
}

function buildJourney() {
  const cities = demoCities.map(city => {
    const completedScenes = city.scenes.filter(scene =>
      demoState.checkins.includes(scene.id)
    ).length;

    const bossCompleted =
      demoState.completedBosses.includes(city.id);

return {
  id: city.id,
  cityId: city.id,

  code: city.code,
  name: city.name,
  cityName: city.name,

  unlockOrder: city.unlockOrder,

  unlocked: demoState.unlockedCities.includes(city.id),
  defeated: bossCompleted,

  status: bossCompleted
    ? "COMPLETED"
    : demoState.unlockedCities.includes(city.id)
      ? "AVAILABLE"
      : "LOCKED",

  done: completedScenes,
  total: city.scenes.length || 3,

  completedStageCount: completedScenes + (bossCompleted ? 1 : 0),
  totalStageCount: (city.scenes.length || 3) + 1,

  completedScenes,
  totalScenes: city.scenes.length || 3,

  completionPercent: city.scenes.length
    ? Math.round(completedScenes / city.scenes.length * 100)
    : 0,

  bossName: city.bossName,
  bossCompleted,

  badgeIcon: bossCompleted ? "🏅" : "✦",

  scenes: city.scenes.map((scene, index) => ({
    ...scene,

    stageOrder: index + 1,
    stageLabel: `第 ${index + 1} 關`,
    stageConfigured: true,

    completed: demoState.checkins.includes(scene.id),

    stageStatus:
      demoState.checkins.includes(scene.id)
        ? "COMPLETED"
        : index === completedScenes
          ? "AVAILABLE"
          : "LOCKED",

    status:
      demoState.checkins.includes(scene.id)
        ? "COMPLETED"
        : index === completedScenes
          ? "AVAILABLE"
          : "LOCKED",

    actionLabel: "開始挑戰",
    interactionType: "QUIZ",
    mysteryChallengeEnabled: true,

    desc: scene.description,
    story: scene.description
  })),

  bossStage: {
    stageOrder: 4,
    stageLabel: "第 4 關",
    bossName: city.bossName,

    stageStatus: bossCompleted
      ? "COMPLETED"
      : completedScenes >= (city.scenes.length || 3)
        ? "AVAILABLE"
        : "LOCKED",

    actionLabel: bossCompleted ? "再次挑戰" : "挑戰守護者"
  }
};
  });

  return {
    success: true,
    message: "取得 Demo 旅程成功",
data: {
  player: demoState.player,

  currentCityId: demoState.currentCityId,
  currentCityCode:
    demoCities.find(city => city.id === demoState.currentCityId)?.code,

  journeyCompleted: demoState.completedBosses.length === 6,

  completedLandmarks: demoState.checkins.length,
  completedLandmarkCount: demoState.checkins.length,

  completedCities: demoState.completedBosses.length,
  completedCityCount: demoState.completedBosses.length,
  totalCityCount: demoCities.length,
  cities
}
  };
}

async function demoApiRequest(path, options = {}) {
  const method = (options.method || "GET").toUpperCase();

  let body = {};

  if (options.body) {
    try {
      body = JSON.parse(options.body);
    } catch {
      body = {};
    }
  }

  if (path.endsWith("/api/auth/login") && method === "POST") {
    return demoLogin(body.username);
  }

  if (path.endsWith("/api/auth/register") && method === "POST") {
    return demoLogin(body.username);
  }

  if (path.endsWith("/api/journey/me") && method === "GET") {
    return buildJourney();
  }

  if (path.endsWith("/api/journey/missions")) {
    return {
      success: true,
      message: "取得每日任務成功",
      data: [
        {
          id: "daily-checkin",
          title: "完成 1 個景點",
          current: Math.min(demoState.checkins.length, 1),
          target: 1,
          completed: demoState.checkins.length >= 1
        }
      ]
    };
  }

  if (path.endsWith("/api/journey/achievements")) {
    return {
      success: true,
      message: "取得成就成功",
      data: demoState.achievements
    };
  }

  if (path.endsWith("/api/collection")) {
    return {
      success: true,
      message: "取得收藏成功",
      data: {
        landmarks: demoCities.flatMap(city =>
          city.scenes.map(scene => ({
            ...scene,
            collected: demoState.checkins.includes(scene.id)
          }))
        ),
        badges: demoCities.map(city => ({
          cityId: city.id,
          name: `${city.name}徽章`,
          collected: demoState.completedBosses.includes(city.id)
        }))
      }
    };
  }

  if (path.endsWith("/api/checkins") && method === "POST") {
    return demoCheckin(body.sceneId);
  }

  return {
    success: false,
    message: `Demo 尚未模擬此功能：${method} ${path}`,
    data: null
  };
}