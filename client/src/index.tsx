import './styles.scss';
import React, { ChangeEvent, SyntheticEvent, useState } from 'react';
import ReactDOM from 'react-dom';
import { ICategory, IPlayer, sendToServer, state, tryAuth } from './logic';

let questionsTableWidth = 40;

function getTextWidth(text: string, font: string) {
    let canvas = document.createElement("canvas")
    var context = canvas.getContext("2d");
    context.font = font;
    var metrics = context.measureText(text);
    return metrics.width;
}

function App() {
    if(state.connecting) {
        return <div></div>
    } else {
        if(state.authorized) {
            return <Game></Game>
        } else {
            return <Auth></Auth>
        }
    }
}

function Auth() {
    const [playerName, setPlayerName] = useState(state.playerName);
    const [key, setKey] = useState("");
    
    let handleNameChange = (e: ChangeEvent<HTMLInputElement>) => setPlayerName(e.target.value);

    let handleKeyChange = (e: ChangeEvent<HTMLInputElement>) => setKey(e.target.value);
    
    let handleButtonClick = (e: SyntheticEvent) => {
        localStorage.setItem("playerName", playerName);
        localStorage.setItem("key", key);
        tryAuth();
    }

    return <div className="auth">
        <div>
            <span>Имя</span><input type="text" onChange={handleNameChange} value={playerName}></input>
        </div>
        <div>
            <span>Код</span><input type="password" onChange={handleKeyChange}></input>
        </div>
        <button onClick={handleButtonClick}>Вход</button>
    </div>
}

function Game() {
    let gamePhaseComponent;
    if(state.gamePhase == "SPLASHSCREEN") {
        gamePhaseComponent = <SplashScreen></SplashScreen>
    }
    if(state.gamePhase == "QUESTIONSTABLE") {
        gamePhaseComponent = <QuestionsTable></QuestionsTable>
    }
    if(state.gamePhase == "QUESTION") {
        gamePhaseComponent = <QuestionBlock></QuestionBlock>
    }
    if(state.gamePhase == "ENDGAME") {
        gamePhaseComponent = <div></div>
    }

    return <div className="game">
        {gamePhaseComponent}
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
    const handleStartGameButton = (e: SyntheticEvent) => {
        sendToServer({type: "startGame"});
    }

    return <div className="splashScreen">
        <div>Ожидание начала игры</div>
        <div>
            { state.permissions == "ADMIN" ? <button onClick={handleStartGameButton}>Начать</button> : null }
        </div>
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
                return <CategoryRow category={category} categoryIndex={ci} questionsInCategory={questionsInCategory} key={ci}></CategoryRow>
            })
        }
    </div>
}

function CategoryRow(prop: {category: ICategory, categoryIndex: number, questionsInCategory:number}) {
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
        let handleButtonClick = (e: SyntheticEvent) => {
            sendToServer({
                type: "questionOpen",
                category: prop.categoryIndex,
                question: i
            });
        }
        result.push(<div className="priceButton" style={priceButtonStyle}  key={i} onClick={handleButtonClick}>{price}</div>);
    }

    return <div className="categoryRow">{result}</div>
}



function QuestionBlock() {
    let t = state.questionText;
    //let t = "В карельской сказке волшебный жернов мог намолоть все, что захочешь. Оправляясь рыбачить, богач взял жернов и велел намолоть этого, да побольше. От тяжести лодка утонула, но жернов и на дне моря продолжал молоть. Что же?"
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

export let render = () => ReactDOM.render(<App />, document.querySelector('#root'));
render();