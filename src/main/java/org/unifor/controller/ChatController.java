package org.unifor.controller;

import org.unifor.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes; // IMPORTANTE: Adicionar este import

import java.util.List;

@Controller
public class ChatController {

    @Autowired
    private ChatService chatService;

    /**
     * Carrega a página principal do chat.
     */
    @GetMapping("/")
    public String chatPage(Model model) {
        model.addAttribute("messages", chatService.getMessages());
        return "chat";
    }

    /**
     * Recebe uma nova mensagem enviada pelo formulário da página.
     */
    @PostMapping("/send")
    public String sendMessage(@RequestParam String message) {
        if (message != null && !message.trim().isEmpty()) {
            chatService.sendMessage(message);
        }
        return "redirect:/";
    }

    /**
     * Endpoint para o AJAX Polling. Retorna a lista de mensagens em formato JSON.
     */
    @GetMapping("/messages")
    @ResponseBody
    public List<String> getMessages() {
        return chatService.getMessages();
    }

    /**
     * NOVO: Endpoint para iniciar a descoberta de peers na rede.
     */
    @PostMapping("/discover")
    public String discoverPeers(RedirectAttributes redirectAttributes) {
        chatService.triggerDiscovery();
        redirectAttributes.addFlashAttribute("feedbackMessage", "🔎 Sinal de descoberta enviado para a rede!");
        redirectAttributes.addFlashAttribute("feedbackType", "success");
        return "redirect:/";
    }

    /**
     * ALTERADO: Recebe a requisição para conectar, agora com feedback para a UI.
     * @param host O IP do peer de destino.
     * @param port A porta do peer de destino.
     * @param redirectAttributes Objeto do Spring para passar atributos através de um redirect.
     * @return Redireciona de volta para a página principal.
     */
    @PostMapping("/connect")
    public String connectToPeer(@RequestParam String host, @RequestParam int port, RedirectAttributes redirectAttributes) {
        if (host != null && !host.trim().isEmpty() && port > 0) {
            // Chama o serviço e captura o resultado da conexão.
            boolean success = chatService.connectToPeer(host, port);

            // Cria uma "Flash Message" com base no resultado.
            if (success) {
                redirectAttributes.addFlashAttribute("feedbackMessage", "✅ Conexão com " + host + ":" + port + " bem-sucedida!");
                redirectAttributes.addFlashAttribute("feedbackType", "success");
            } else {
                redirectAttributes.addFlashAttribute("feedbackMessage", "❌ Falha ao conectar com " + host + ":" + port + ". Verifique o console para detalhes.");
                redirectAttributes.addFlashAttribute("feedbackType", "error");
            }
        }
        return "redirect:/"; // Redireciona de volta para a página principal
    }
}