import './styles.scss';
import React from 'react';
import ReactDOM from 'react-dom';

interface IPlayer {
    avatar: string,
    name: string,
    points: number,
    online: boolean
}

interface ICategory {
    name: string,
    questions: Array<number>
}

let state = {
    gamePhase: "splashscreen",
    localPlayerShouldAnswer: false,
    categories: [
        {
            name: "Category 1",
            questions: [100, 200, 300, 400, 500]
        },
        {
            name: "Category 2",
            questions: [100, 200, 300, 400, 500]
        },
        {
            name: "Category 3",
            questions: [100, 200, 300, 400, 500]
        },
        {
            name: "Category 4",
            questions: [100, 200, 300, 400, 500]
        },
        {
            name: "Category 5",
            questions: [100, 200, 300, 400, 500]
        },
        {
            name: "Category 6",
            questions: [100, 200, 300, 400, 500]
        }
    ],
    players: [
        {
            avatar: "jett-avatar.jpg",
            name: "Player 1",
            points: 24500,
            online: true
        },
        {
            avatar: "jett-avatar.jpg",
            name: "Player 2",
            points: 31900,
            online: true
        },
        {
            avatar: "smorc.webp",
            name: "Player 3",
            points: -300,
            online: true
        }
    ]
}

let questionsTableWidth = 40;

function getTextWidth(text: string, font: string) {
    let canvas = document.createElement("canvas")
    var context = canvas.getContext("2d");
    context.font = font;
    var metrics = context.measureText(text);
    return metrics.width;
}

function App() {
    return <Game></Game>
}

function Game() {
    let q;
    if(state.gamePhase == "splashscreen") {
        q = <SplashScreen></SplashScreen>
    }
    if(state.gamePhase == "table") {
        q = <QuestionsTable></QuestionsTable>
    }
    if(state.gamePhase == "question") {
        q = <QuestionBlock></QuestionBlock>
    }

    return <div className="game">
        {q}
        <div className="players-container">
            {
                state.players.map((e) => {
                    return <Player data={e} key={e.name}></Player>
                })
            }
        </div>
        { state.localPlayerShouldAnswer ? <div className="screenLightLeft"></div> : null}
        { state.localPlayerShouldAnswer ? <div className="screenLightRight"></div> : null}    
    </div>
}

function SplashScreen() {
    return <div className="splashScreen">
        Ожидание начала игры
    </div>
}

function QuestionsTable() {
    let questionsInCategory = 0;
    state.categories.forEach((e) => {
        if(e.questions.length > questionsInCategory) {
            questionsInCategory = e.questions.length;
        }
    });

    return <div className="questionsTable" style={{width: `${questionsTableWidth}vw`}}>
        {
            state.categories.map((category, ci) => {
                return <CategoryRow category={category} questionsInCategory={questionsInCategory} key={ci}></CategoryRow>
            })
        }
    </div>
}

function CategoryRow(prop: {category: ICategory, questionsInCategory:number}) {
    let result = [];
    let categoryStyle = {
        width: (questionsTableWidth * 0.35) + "vw",
        height: (questionsTableWidth * 0.65 / prop.questionsInCategory) + "vw"
    }
    let priceButtonStyle = {
        width: (questionsTableWidth * 0.65 / prop.questionsInCategory) + "vw",
        height: (questionsTableWidth * 0.65 / prop.questionsInCategory) + "vw"
    }
    result.push(<div className="category" style={categoryStyle} key="category">{prop.category.name}</div>);
    for(let i = 0; i < prop.questionsInCategory; i++) {
        let price;
        if(i < prop.category.questions.length) {
            price = prop.category.questions[i];
        } else {
            price = "";
        }
        result.push(<div className="priceButton" style={priceButtonStyle}  key={i}>{price}</div>);
    }

    return <div className="categoryRow">{result}</div>
}



function QuestionBlock() {
    let t = "В карельской сказке волшебный жернов мог намолоть все, что захочешь. Оправляясь рыбачить, богач взял жернов и велел намолоть этого, да побольше. От тяжести лодка утонула, но жернов и на дне моря продолжал молоть. Что же?"
    // let t = "КЕК";
    let textWidth = getTextWidth(t, "1.0vw Futura Condensed");
    let questionsTableWidthInPx = window.innerWidth * questionsTableWidth / 100;
    let fontSize = 4.5 / (textWidth / questionsTableWidthInPx);
    if(fontSize > 3.0) {
        fontSize = 3.0;
    }
    if(fontSize < 0.8) {
        fontSize = 0.8;
    }

    return <div className="questionBlock" style={{width: questionsTableWidth + "vw", height: (questionsTableWidth * 0.65) + "vw", fontSize: `${fontSize}vw`}}>
        {t}
    </div>
}

function Player(prop: {data: IPlayer}) {
    return <div className="player">
        <img src={prop.data.avatar} className="avatar"></img>
        <div className="points">{prop.data.points}</div>
        <div className="name">{prop.data.name}</div>
    </div>
}

let render = () => ReactDOM.render(<App />, document.querySelector('#root'));
render();





let socket = new WebSocket("ws://localhost:8080/");

socket.onopen = (e) => {
    console.log("connected");
}

socket.onmessage = (e) => {
    let message = JSON.parse(e.data);
}