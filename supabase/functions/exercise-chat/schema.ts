// Strict request validation for `exercise-chat`. Unknown keys are rejected (unlike food-photo): only the
// exercise and the question may reach the model. Caps leave room over the catalogue (longest instructions
// text 804 chars, longest name 61; custom names up to 100).

export const MAX_NAME = 120;
export const MAX_MUSCLES = 12;
export const MAX_MUSCLE = 60;
export const MAX_INSTRUCTIONS = 4000;
export const MAX_QUESTION = 500;

export type ChatInput = {
    exercise: { name: string; muscles: string[]; instructions: string };
    question: string;
};

const isObject = (v: unknown): v is Record<string, unknown> =>
    v !== null && typeof v === "object" && !Array.isArray(v);

function extraKey(obj: Record<string, unknown>, allowed: string[]) {
    return Object.keys(obj).find((k) => !allowed.includes(k));
}

/** Returns the normalized input, or an error message string. */
export function parseChatInput(body: unknown): ChatInput | string {
    if (!isObject(body)) return "Body must be a JSON object";
    if (extraKey(body, ["exercise", "question"]) !== undefined) {
        return "Only exercise and question are allowed";
    }
    const ex = body.exercise;
    if (!isObject(ex)) return "exercise is required";
    if (extraKey(ex, ["name", "muscles", "instructions"]) !== undefined) {
        return "exercise only allows name, muscles and instructions";
    }

    if (typeof ex.name !== "string") return "exercise.name is required";
    const name = ex.name.trim();
    if (name === "" || name.length > MAX_NAME) {
        return `exercise.name must have 1 to ${MAX_NAME} characters`;
    }

    if (!Array.isArray(ex.muscles)) return "exercise.muscles must be an array";
    if (ex.muscles.length < 1 || ex.muscles.length > MAX_MUSCLES) {
        return `exercise.muscles must have 1 to ${MAX_MUSCLES} items`;
    }
    const muscles: string[] = [];
    for (const m of ex.muscles) {
        if (typeof m !== "string") return "exercise.muscles must be strings";
        const t = m.trim();
        if (t === "" || t.length > MAX_MUSCLE) {
            return `each muscle must have 1 to ${MAX_MUSCLE} characters`;
        }
        muscles.push(t);
    }

    if (typeof ex.instructions !== "string") {
        return "exercise.instructions must be a string";
    }
    const instructions = ex.instructions.trim();
    if (instructions.length > MAX_INSTRUCTIONS) {
        return `exercise.instructions must have at most ${MAX_INSTRUCTIONS} characters`;
    }

    if (typeof body.question !== "string") return "question is required";
    const question = body.question.trim();
    if (question === "" || question.length > MAX_QUESTION) {
        return `question must have 1 to ${MAX_QUESTION} characters`;
    }

    return { exercise: { name, muscles, instructions }, question };
}
